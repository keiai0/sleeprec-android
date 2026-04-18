package io.github.keiai0.sleeprec

import java.text.Normalizer

/** 睡眠前タグの検証(FR-2.10: 最大 20 文字、重複不可)。Android に依存しない純粋な関数。 */
object TagRules {
    const val MAX_LENGTH = 20
    const val MAX_TAGS = 10 // 1 回の計測に付けられる数(画面に収まる範囲の上限。SPEC に定めはない)

    enum class Error { EMPTY, TOO_LONG, DUPLICATE, TOO_MANY }

    /** 前後の空白を除き、連続する空白を 1 つにする。 */
    fun clean(input: String): String = input.trim().replace(Regex("\\s+"), " ")

    // 重複の判定用。全角/半角、大文字/小文字の違いは同じタグとみなす(「Coffee」と「ｃｏｆｆｅｅ」)
    private fun key(tag: String): String = Normalizer.normalize(tag, Normalizer.Form.NFKC).lowercase()

    /** 追加できるなら null、できないなら理由を返す。文字数は、見た目の文字数(サロゲートペアも 1 文字)で数える。 */
    fun validate(input: String, existing: List<String>): Error? {
        val tag = clean(input)
        return when {
            tag.isEmpty() -> Error.EMPTY
            tag.codePointCount(0, tag.length) > MAX_LENGTH -> Error.TOO_LONG
            existing.any { key(it) == key(tag) } -> Error.DUPLICATE
            existing.size >= MAX_TAGS -> Error.TOO_MANY
            else -> null
        }
    }
}
