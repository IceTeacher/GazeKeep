package cn.rainafter.app.gazekeep.ui.text

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** 中英字符串资源必须键集合一致、格式占位符一致；缺键或占位符错位会在切换语言后崩溃或错显。 */
class StringResourcesTest {
  // Gradle 主机测试的工作目录是模块目录。
  private val root = File("src/commonMain/composeResources")

  private fun strings(path: String): Map<String, String> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, path))
    val nodes = document.getElementsByTagName("string")
    val entries = (0 until nodes.length).map { nodes.item(it) as Element }.map {
      it.getAttribute("name") to
        it.textContent
    }
    val map = entries.toMap()
    assertEquals(entries.size, map.size, "$path 存在重复字符串键")
    return map
  }

  private fun placeholders(text: String?) = Regex("""%\d+\$[ds]""").findAll(text.orEmpty()).map {
    it.value
  }.sorted().toList()

  @Test
  fun englishResourcesCoverDefaultKeysWithMatchingPlaceholders() {
    val zh = strings("values/strings.xml")
    val en = strings("values-en/strings.xml")
    assertEquals(zh.keys.sorted(), en.keys.sorted())
    for ((key, value) in zh) assertEquals(placeholders(value), placeholders(en[key]), key)
  }
}
