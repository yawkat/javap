package at.yawk.javap.model

import kotlinx.serialization.Serializable

@Serializable
data class PasteDto(
        val id: String,
        val editable: Boolean,
        val input: ProcessingInput,
        val output: ProcessingOutput
) {
    /**
     * For POST request
     */
    @Serializable
    data class Create(
            val input: ProcessingInput
    )

    /**
     * For PUT request
     */
    @Serializable
    data class Update(
            val input: ProcessingInput? = null
    )
}