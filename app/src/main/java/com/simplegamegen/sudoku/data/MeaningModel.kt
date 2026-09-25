package com.simplegamegen.sudoku.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.simplegamegen.sudoku.wordplay.EmbeddingJudge
import com.simplegamegen.sudoku.wordplay.SentenceEmbedder
import com.simplegamegen.sudoku.wordplay.WordPiece
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * The on-device sentence model (all-MiniLM-L6-v2, quantised, Apache-2.0) used by Word Meaning when
 * word checks can't decide. Loaded on first use; nothing leaves the device.
 */
class MeaningModel(private val context: Context) : SentenceEmbedder {
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val session: OrtSession by lazy {
        val bytes = context.assets.open("models/minilm/model.onnx").use { it.readBytes() }
        env.createSession(bytes, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })
    }
    private val tokenizer: WordPiece by lazy {
        WordPiece(context.assets.open("models/minilm/vocab.txt").bufferedReader().readLines())
    }

    val judge: EmbeddingJudge by lazy { EmbeddingJudge(this) }

    override fun embed(text: String): FloatArray = synchronized(this) {
        val ids = tokenizer.encode(text).map { it.toLong() }.toLongArray()
        val shape = longArrayOf(1, ids.size.toLong())
        fun tensor(values: LongArray) = OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape)
        tensor(ids).use { input ->
            tensor(LongArray(ids.size) { 1 }).use { mask ->
                tensor(LongArray(ids.size)).use { types ->
                    session.run(mapOf("input_ids" to input, "attention_mask" to mask, "token_type_ids" to types)).use { out ->
                        @Suppress("UNCHECKED_CAST")
                        val tokens = (out[0].value as Array<Array<FloatArray>>)[0]
                        // Mean over tokens, then unit length, as sentence-transformers does (the mean's
                        // length divides out, so the sum is normalised directly).
                        val v = FloatArray(tokens[0].size)
                        tokens.forEach { t -> for (i in v.indices) v[i] += t[i] }
                        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-9f)
                        for (i in v.indices) v[i] /= norm
                        v
                    }
                }
            }
        }
    }
}
