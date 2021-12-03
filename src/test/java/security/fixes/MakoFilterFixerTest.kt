package security.fixes

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyCallExpression
import junit.framework.TestCase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import security.SecurityTestTask

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MakoFilterFixerTest: SecurityTestTask() {
    @BeforeAll
    override fun setUp() {
        super.setUp()
    }

    @AfterAll
    override fun tearDown(){
        super.tearDown()
    }

    @Test
    fun `verify fixer properties`(){
        val fixer = MakoFilterFixer()
        assertTrue(fixer.startInWriteAction())
        assertTrue(fixer.familyName.isNotBlank())
        assertTrue(fixer.name.isNotBlank())
        assertTrue(fixer.text.isNotBlank())
    }

    private fun getNewFileForCode(code: String): String {
        var result: String = ""
        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("test.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = MakoFilterFixer()
            val expr = PsiTreeUtil.findChildrenOfType(testFile, PyCallExpression::class.java).first()
            result = fixer.runFix(project, testFile, expr)?.text ?: ""
        }
        return result.replace(" ","").replace("\n", "")
    }

    @Test
    fun `replace environment with no args`(){
        val code = """
            import mako.template
            env = mako.template.Template('xyz')
        """.trimIndent()
        val newCode = getNewFileForCode(code)
        TestCase.assertEquals("mako.template.Template('xyz',default_filters=['h'])", newCode)
    }

    @Test
    fun `replace environment with nested keyword args`(){
        val code = """
            import mako.template
            env = mako.template.Template(str(value='xyz'))
        """.trimIndent()
        val newCode = getNewFileForCode(code)
        TestCase.assertEquals("mako.template.Template(str(value='xyz'),default_filters=['h'])", newCode)
    }

    @Test
    fun `replace environment with existing arg`(){
        val code = """
            import mako.template
            env = mako.template.Template('xyz', default_filters=['h'])
        """.trimIndent()
        val newCode = getNewFileForCode(code)
        TestCase.assertEquals(newCode, "")
    }


    @Test
    fun `test batch fix`(){
        val mockCaretModel = mock<CaretModel> {
            on { offset } doReturn 29
        }
        val mockEditor = mock<Editor> {
            on { caretModel } doReturn mockCaretModel
        }
        val code = """
            import mako.template
            env = mako.template.Template('xyz')
        """.trimIndent()

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = MakoFilterFixer()
            assertTrue(fixer.isAvailable(project, mockEditor, testFile))
            val expr: MutableCollection<PyCallExpression> = PsiTreeUtil.findChildrenOfType(testFile, PyCallExpression::class.java)
            assertNotNull(expr)
            expr.forEach { e ->
                val mockProblemDescriptor = mock<ProblemDescriptor> {
                    on { psiElement } doReturn(e)
                }
                fixer.applyFix(project, mockProblemDescriptor)
                assertNotNull(e)
                verify(mockProblemDescriptor, times(2)).psiElement
            }
        }
    }
}