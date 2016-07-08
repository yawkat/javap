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
        if (paste.contains("/")) {
            outputType = paste.substr(paste.indexOf("/") + 1);
            paste = paste.substr(0, paste.indexOf("/"));
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