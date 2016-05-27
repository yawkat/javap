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

    function displayPaste(paste) {
        currentPaste = paste;
        if (paste.id == "default") {
            delete window.location.hash;
        } else {
            window.location.hash = paste.id;
        }

        setEditorValue(codeEditor, paste.input.code);
        var compiledSuccessfully = !!paste.output.javap;
        $("body").toggleClass("compile-error", !compiledSuccessfully);
        if (compiledSuccessfully) {
            setEditorValue(resultEditor, paste.output.javap);
        } else {
            setEditorValue(resultEditor, paste.output.compilerLog);
        }
        $("#compiler-names").find("option").each(function () {
            var tgt = $(this);
            tgt.attr("selected", tgt.val() === paste.input.compilerName);
        });
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
            displayPaste(data);
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
    resultEditor.setReadOnly(true);

    function setEditorValue(editor, value) {
        var selection = editor.selection.getRange();
        editor.setValue(value);
        editor.selection.setRange(selection);
    }

    var currentPaste = null;

    ajax({
        method: "GET",
        url: "/api/compiler"
    }).then(function (compilers) {
        var compilerNames = $("#compiler-names");
        compilerNames.empty();
        for (var i = 0; i < compilers.length; i++) {
            var option = $("<option>");
            compilerNames.append(option);
            option.text(compilers[i].name);
            option.val(compilers[i].name);
        }

        ajax({
            method: "GET",
            url: "/api/paste/" + (window.location.hash || "#default").substr(1)
        }).then(function (data) {
            displayPaste(data)
        }, handleError);
    }, handleError);

    $("#compile").click(triggerCompile);

    $(document).tooltip();
});