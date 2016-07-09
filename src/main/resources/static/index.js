$(function () {
    // AJAX

    function ajax(request) {
        var userToken;

        var userTokenMatch = /userToken=(\w+)/.exec(document.cookie);
        if (!userTokenMatch) {
            var chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            userToken = "";
            while (userToken.length < 64) {
                userToken += chars.charAt(Math.floor(Math.random() * chars.length));
            }
            var expiryTime = new Date();
            expiryTime.setTime(expiryTime.getTime() + (120 * 24 * 60 * 60 * 1000)); // 4 months
            document.cookie = "userToken=" + userToken + "; expires=" + expiryTime.toUTCString();
        } else {
            userToken = userTokenMatch[1];
        }

        var oldBeforeSend = request.beforeSend;
        request.beforeSend = function (xhr) {
            xhr.setRequestHeader("X-User-Token", userToken);
            if (oldBeforeSend) {
                oldBeforeSend(xhr);
            }
        };
        return $.ajax(request);
    }

    function showDialog(title, message) {
        var dialog = $("#dialog-message");
        dialog.attr("title", title);
        dialog.text(message);
        dialog.dialog({
            modal: true,
            buttons: {
                Ok: function () {
                    $(this).dialog("close");
                }
            }
        });
    }

    function handleError(xhr, status, msg) {
        var message = msg;
        //noinspection JSUnresolvedVariable
        if (xhr.responseJSON && xhr.responseJSON.message) {
            //noinspection JSUnresolvedVariable
            message = xhr.responseJSON.message;
        }
        showDialog("Error", message);
    }

    // PASTE FUNCTIONS

    function showCurrentPasteOutput(type) {
        $("body").toggleClass("compile-error", type === "compilerLog");
        setEditorValue(resultEditor, currentPaste.output[type]);
        clearGutterDecorations();
        if (type === "javap") {
            colorizeJavap();
        }
        if (/^default:.*$/.exec(currentPaste.id)) {
            delete window.location.hash;
        } else {
            var path = currentPaste.id;
            if (type !== "javap" && (type !== "compilerLog" || currentPaste.output["javap"])) {
                path += "/" + type;
            }
            window.location.hash = path;
        }
    }

    function displayPaste(paste, requestedOutputType) {
        currentPaste = paste;

        setEditorValue(codeEditor, paste.input.code);
        $("#compiler-names").find("option").each(function () {
            var tgt = $(this);
            tgt.attr("selected", tgt.val() === paste.input.compilerName);
        });

        var outputType = $("#output-type");
        outputType.find("option").each(function () {
            var option = $(this);
            var enabled = option.val() in paste.output;
            if (enabled) {
                var value = paste.output[option.val()];
                enabled &= !!value && value.trim() !== "";
            }
            option.attr("disabled", !enabled);
        });
        if (requestedOutputType) {
            outputType.find(":selected").attr("selected", false);
            outputType.find("[value=" + requestedOutputType + "]").attr("selected", true);
        }
        var selected = outputType.find(":selected");
        if (selected.attr("disabled")) {
            selected.attr("selected", false);
            selected = outputType.find(":enabled").first();
            selected.attr("selected", true);
        }
        showCurrentPasteOutput(selected.val());
    }

    function loadPaste(name, outputType, forceCompiler) {
        ajax({
            method: "GET",
            url: "/api/paste/" + name
        }).then(function (data) {
            if (forceCompiler) {
                data.input.compilerName = forceCompiler;
            }
            displayPaste(data, outputType);
        }, handleError);
    }

    function triggerCompile() {
        $("body").addClass("compiling");
        ajax({
            method: currentPaste.editable ? "PUT" : "POST",
            url: "/api/paste" + (currentPaste.editable ? "/" + currentPaste.id : ""),
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify({
                input: {
                    code: codeEditor.getValue(),
                    compilerName: $("#compiler-names").val()
                }
            })
        }).then(function (data) {
            displayPaste(data, null);
        }, handleError).always(function () {
            $("body").removeClass("compiling");
        });
    }

    // JAVAP LINE COLORING

    // returns a dict `source line -> javap line array`
    function buildJavapLineMappings() {
        var javapLines = currentPaste.output.javap.split("\n");

        var OUTER = 0;
        var METHOD_BYTECODE = 1;
        var METHOD_BEFORE_LNT = 2;
        var METHOD_LNT = 3;

        var mode = OUTER;

        var currentMethodLabels;
        var currentMethodBytecodeStart;
        var currentMethodBytecodeEnd;
        var currentMethodLNT;

        var mapping = {};

        for (var i = 0; i < javapLines.length; i++) {
            var line = javapLines[i];
            switch (mode) {
                case OUTER:
                    if (line === "    Code:") {
                        mode = METHOD_BYTECODE;

                        currentMethodLabels = {};
                        currentMethodLNT = {};
                        currentMethodBytecodeStart = i + 1;
                    }
                    break;
                case METHOD_BYTECODE:
                    if (line !== "      LineNumberTable:" && line !== "      LocalVariableTable:") {
                        var labelPattern = /^\s*(\d+): .*$/;
                        var match = labelPattern.exec(line);
                        if (match) {
                            var label = parseInt(match[1]);
                            currentMethodLabels[i] = label;
                        }
                    } else {
                        currentMethodBytecodeEnd = i;

                        mode = METHOD_BEFORE_LNT;
                        i--;
                    }
                    break;
                case METHOD_BEFORE_LNT:
                    if (line === "      LineNumberTable:") {
                        mode = METHOD_LNT;
                    }
                    break;
                case METHOD_LNT:
                    var entryPattern = /^        line (\d+): (\d+)$/;
                    var match = entryPattern.exec(line);
                    if (match) {
                        var sourceLine = parseInt(match[1]) - 1; // 1-indexed by javac
                        var label = parseInt(match[2]);
                        currentMethodLNT[label] = sourceLine;
                    } else {
                        var currentLine = null;
                        for (var j = currentMethodBytecodeStart; j < currentMethodBytecodeEnd; j++) {
                            var label = currentMethodLabels[j];
                            var sourceLine = currentMethodLNT[label];
                            if (sourceLine || sourceLine === 0) {
                                currentLine = [];
                                mapping[sourceLine] = currentLine;
                            }
                            if (currentLine !== null) {
                                currentLine.push(j);
                            }
                        }

                        mode = OUTER;
                    }
                    break;
            }
        }
        return mapping;
    }

    var gutterDecorationsLeft = {};
    var gutterDecorationsRight = {};

    function clearGutterDecorations() {
        for (var i = 0; i < Object.keys(gutterDecorationsLeft).length; i++) {
            var line = Object.keys(gutterDecorationsLeft)[i];
            codeEditor.session.removeGutterDecoration(line, gutterDecorationsLeft[line]);
        }
        for (var i = 0; i < Object.keys(gutterDecorationsRight).length; i++) {
            var line = Object.keys(gutterDecorationsRight)[i];
            resultEditor.session.removeGutterDecoration(line, gutterDecorationsRight[line]);
        }
        gutterDecorationsLeft = {};
        gutterDecorationsRight = {};
    }

    function colorizeJavap() {
        var lineMappings = buildJavapLineMappings();
        var colorIndex = 0;
        var lineCount = currentPaste.input.code.split("\n").length;
        for (var i = 0; i < Object.keys(lineMappings).length; i++) {
            var sourceLine = Object.keys(lineMappings)[i];
            if (sourceLine >= lineCount) {
                continue;
            }
            var outputLines = lineMappings[sourceLine];
            var cl = "line-color-" + colorIndex;
            codeEditor.session.addGutterDecoration(sourceLine, cl);
            gutterDecorationsLeft[sourceLine] = cl;
            for (var j = 0; j < outputLines.length; j++) {
                var outputLine = outputLines[j];
                resultEditor.session.addGutterDecoration(outputLine, cl);
                gutterDecorationsRight[outputLine] = cl;
            }
            colorIndex = (colorIndex + 1) % 8;
        }
    }

    // MAKE STUFF INTERACTIVE

    var codeEditor = ace.edit("code-editor");
    codeEditor.getSession().setMode("ace/mode/java");

    codeEditor.commands.addCommand({
        name: "trigger compile ctrl-enter",
        bindKey: { win: "Ctrl-Enter", mac: "Command-Enter" },
        exec: triggerCompile,
        readOnly: false
    });
    codeEditor.commands.addCommand({
        name: "trigger compile ctrl-s",
        bindKey: { win: "Ctrl-S", mac: "Command-S" },
        exec: triggerCompile,
        readOnly: false
    });

    var resultEditor = ace.edit("result-editor");
    resultEditor.getSession().setMode("ace/mode/java");
    resultEditor.getSession().setUseWrapMode(true);
    resultEditor.setReadOnly(true);

    function setEditorValue(editor, value) {
        var selection = editor.selection.getRange();
        editor.setValue(value);
        editor.selection.setRange(selection);
    }

    var currentPaste = null;

    ajax({
        method: "GET",
        url: "/api/sdk"
    }).then(function (sdks) {
        var compilerNames = $("#compiler-names");
        compilerNames.empty();
        for (var i = 0; i < sdks.length; i++) {
            var option = $("<option>");
            option.data("sdk", sdks[i]);
            compilerNames.append(option);
            option.text(sdks[i].name);
            option.val(sdks[i].name);
        }

        var paste = (window.location.hash || "#default:JAVA").substr(1);
        var outputType = null;
        var slashIndex = paste.indexOf("/");
        if (slashIndex !== -1) {
            outputType = paste.substr(slashIndex + 1);
            paste = paste.substr(0, slashIndex);
        }
        loadPaste(paste, outputType);
    }, handleError);

    $("#compile").click(triggerCompile);

    var selectedLanguage = "JAVA";
    $("#compiler-names").change(function () {
        var newSdk = $(this).find(":selected").data("sdk");
        if (newSdk.language !== selectedLanguage) {
            loadPaste("default:" + newSdk.language, null, newSdk.name);
        }
    });

    $("#output-type").change(function () {
        showCurrentPasteOutput($(this).val());
    });

    $(document).tooltip();
});
