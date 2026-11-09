package pt.studiomesa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioMesaExactMathToolInstrumentedTest {
    @Test
    fun toolReturnsDeterministicReadOnlyResultAndRejectsInvalidExpression() {
        val tool = StudioMesaExactMathTool()
        val definition = JSONObject(tool.getToolDescriptionJsonString())
        assertEquals("calculate_exact_math", definition.getString("name"))

        val success = JSONObject(
            tool.execute(JSONObject().put("expression", "(12.5 + 7.5) * 2").toString())
        )
        assertEquals("40", success.getString("result"))
        assertTrue(success.getBoolean("read_only"))

        val invalid = JSONObject(
            tool.execute(JSONObject().put("expression", "1 / 0").toString())
        )
        assertTrue(invalid.has("error"))
        assertTrue(invalid.getBoolean("read_only"))
    }
}
