package at.yawk.javap

import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import kotlin.browser.document
import kotlin.dom.appendElement
import kotlin.dom.clear

object SdkManager {
    private val compilerNames = document.getElementById("compiler-names") as HTMLSelectElement

    private lateinit var options: Map<Sdk, HTMLOptionElement>
    private lateinit var selectedSdk: Sdk

    var selectedSdkName: String
        get() = selectedSdk.name
        set(name) {
            val sdk = Sdks.sdksByName[name] ?: throw NoSuchElementException(name)
            selectedSdk = sdk
            options.getValue(sdk).selected = true

            onChangeSelect()
        }

    init {
        compilerNames.addEventListener("change", {
            val sdk = Sdks.sdksByName.getValue(compilerNames.value)
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
        compilerNames.clear()

        val options = mutableMapOf<Sdk, HTMLOptionElement>()

        for ((category, sdks) in Sdks.sdkByLabel) {
            compilerNames.appendElement("option") {
                require(this is HTMLOptionElement)
                disabled = true
                text = "$category:"
            }

            for (sdk in sdks) {
                compilerNames.appendElement("option") {
                    require(this is HTMLOptionElement)
                    value = sdk.name
                    text = sdk.name
                    options[sdk] = this
                }
            }
        }

        this.options = options

        ready()
    }
}