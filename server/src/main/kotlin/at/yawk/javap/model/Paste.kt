package at.yawk.javap.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * @author yawkat
 */
@JsonIgnoreProperties("ownerToken")
data class Paste(
        val id: String,
        val ownerToken: String,

        val input: ProcessingInput,
        val output: ProcessingOutput
)