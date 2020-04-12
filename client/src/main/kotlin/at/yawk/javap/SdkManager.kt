package at.yawk.javap

import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import kotlin.browser.document
import kotlin.dom.appendElement
import kotlin.dom.clear

object SdkManager {
    private data class SdkDto(val name: String, val language: String)
    private class Sdk(val name: String, val language: SdkLanguage, val option: HTMLOptionElement)

    private val compilerNames = document.getElementById("compiler-names") as HTMLSelectElement

    private lateinit var sdks: Map<String, Sdk>
    private lateinit var selectedSdk: Sdk

    var selectedSdkName: String
        get() = selectedSdk.name
        set(name) {
            val sdk = sdks[name] ?: throw NoSuchElementException(name)
            selectedSdk = sdk
            sdk.option.selected = true

            onChangeSelect()
        }

    init {
        compilerNames.addEventListener("change", {
            val sdk = sdks.getValue(compilerNames.value)
            if (sdk.language != selectedSdk.language) {
                loadPaste("default:${sdk.language}", outputType = null, forceCompiler = sdk.name)
            }
            selectedSdk = sdk

            onChangeSelect()
        })
    }

    private fun onChangeSelect() {
        Editors.setLanguage(selectedSdk.language)
    }

    fun loadSdks(ready: () -> Unit) {
        ajax(Request(
                method = "GET",
                url = "/api/sdk"
        )).then({ sdkArray: Array<SdkDto> ->
            compilerNames.clear()

            fun categoryForSdkName(name: String) =
                    name.match("""^((?:[A-Za-z]+ )+).*$""")!![1]

            val sdkMap = mutableMapOf<String, Sdk>()

            var currentCategory: String? = null
            for (sdk in sdkArray) {
                if (categoryForSdkName(sdk.name) != currentCategory) {
                    currentCategory = categoryForSdkName(sdk.name)
                    compilerNames.appendElement("option") {
                        require(this is HTMLOptionElement)
                        disabled = true
                        text = "${currentCategory.trim()}:"
                    }
                }

                compilerNames.appendElement("option") {
                    require(this is HTMLOptionElement)
                    value = sdk.name
                    text = sdk.name
                    sdkMap[sdk.name] = Sdk(sdk.name, SdkLanguage.valueOf(sdk.language), this)
                }
            }
            sdks = sdkMap

            ready()
        }, handleError)
    }
}