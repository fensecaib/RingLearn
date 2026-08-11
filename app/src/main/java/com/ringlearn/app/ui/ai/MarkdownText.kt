package com.ringlearn.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Markdown 块级元素（轻量解析，仅支持本应用需要的最小集）。 */
internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val code: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val index: Int, val text: String) : MdBlock
}

/** 解析 Markdown 为块列表：代码块 / 标题 / 无序列表 / 有序列表 / 段落；先剥离原始 HTML。 */
internal fun parseMarkdown(text: String): List<MdBlock> {
    val result = mutableListOf<MdBlock>()
    val plain = text.replace(Regex("<[^>]+>"), "")
    val lines = plain.split("\n")
    var inCode = false
    val codeLines = mutableListOf<String>()

    fun flushCode() {
        if (codeLines.isNotEmpty()) {
            result.add(MdBlock.Code(codeLines.joinToString("\n")))
            codeLines.clear()
        }
    }

    for (raw in lines) {
        val trimmed = raw.trim()
        if (inCode) {
            if (trimmed.startsWith("```")) {
                inCode = false
                flushCode()
            } else {
                codeLines.add(raw)
            }
            continue
        }
        when {
            trimmed.startsWith("```") -> inCode = true
            trimmed.isEmpty() -> Unit
            trimmed.startsWith("###") -> result.add(MdBlock.Heading(3, trimmed.removePrefix("###").trim()))
            trimmed.startsWith("##") -> result.add(MdBlock.Heading(2, trimmed.removePrefix("##").trim()))
            trimmed.startsWith("#") -> result.add(MdBlock.Heading(1, trimmed.removePrefix("#").trim()))
            trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                result.add(MdBlock.Bullet(trimmed.drop(2).trim()))
            trimmed.matches(Regex("""\d+\..*""")) ->
                result.add(
                    MdBlock.Numbered(
                        index = trimmed.substringBefore(".").toIntOrNull() ?: 0,
                        text = trimmed.substringAfter(".").trim()
                    )
                )
            else -> result.add(MdBlock.Paragraph(trimmed))
        }
    }
    if (inCode) flushCode()
    return result
}

/** 行内样式：**加粗** / `行内代码` / *斜体*。 */
internal fun buildInline(
    text: String,
    bold: SpanStyle,
    code: SpanStyle,
    italic: SpanStyle
): AnnotatedString {
    val regex = Regex("""(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)""")
    return buildAnnotatedString {
        var last = 0
        for (m in regex.findAll(text)) {
            append(text.substring(last, m.range.first))
            val token = m.value
            when {
                token.startsWith("**") -> withStyle(bold) { append(token.removePrefix("**").removeSuffix("**")) }
                token.startsWith("`") -> withStyle(code) { append(token.removePrefix("`").removeSuffix("`")) }
                token.startsWith("*") -> withStyle(italic) { append(token.removePrefix("*").removeSuffix("*")) }
            }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

/** 聊天气泡内 Markdown 渲染（自绘，无第三方库）。 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(text) { parseMarkdown(text) }
    val bold = style.toSpanStyle().copy(fontWeight = FontWeight.Bold)
    val code = style.toSpanStyle().copy(
        fontFamily = FontFamily.Monospace,
        background = MaterialTheme.colorScheme.surfaceContainerHigh,
        color = MaterialTheme.colorScheme.primary
    )
    val italic = style.toSpanStyle().copy(fontStyle = FontStyle.Italic)
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val hStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }.copy(color = color, fontWeight = FontWeight.Bold)
                    Text(
                        text = buildInline(block.text, bold, code, italic),
                        style = hStyle,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    )
                }
                is MdBlock.Paragraph -> Text(
                    text = buildInline(block.text, bold, code, italic),
                    style = style.copy(color = color)
                )
                is MdBlock.Bullet -> Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text(text = "• ", style = style.copy(color = color))
                    Text(
                        text = buildInline(block.text, bold, code, italic),
                        style = style.copy(color = color)
                    )
                }
                is MdBlock.Numbered -> Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text(text = "${block.index}. ", style = style.copy(color = color, fontWeight = FontWeight.Medium))
                    Text(
                        text = buildInline(block.text, bold, code, italic),
                        style = style.copy(color = color)
                    )
                }
                is MdBlock.Code -> {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}


