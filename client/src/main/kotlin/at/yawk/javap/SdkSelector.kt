package at.yawk.javap

import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import kotlin.browser.document
import kotlin.dom.appendElement
import kotlin.dom.clear

object SdkSelector {
    private val compilerNames = document.getElementById("compiler-names") as HTMLSelectElement

    private lateinit var options: Map<Sdk, HTMLOptionElement>
    private lateinit var _selectedSdk: Sdk

    var selectedSdk: Sdk
        get() = _selectedSdk
        set(sdk) {
            _selectedSdk = sdk
            options.getValue(sdk).selected = true

            onChangeSelect()
        }

    init {
        compilerNames.addEventListener("change", {
            val sdk = Sdks.sdksByName.getValue(compilerNames.value)
            if (sdk.language != _selectedSdk.language) {
                loadPaste("default:${sdk.language}", outputType = null, forceCompiler = sdk.name)
            }
            _selectedSdk = sdk

            onChangeSelect()
        })
    }

    private fun onChangeSelect() {
        Editors.setLanguage(_selectedSdk.language)
        CompilerConfigUi.updateSdk()
    }

    fun loadSdks() {
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
    }
}