package security.fixes

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyBinaryExpression
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import security.SecurityTestTask

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UseCompareDigestFixerTest: SecurityTestTask() {
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
        val fixer = UseCompareDigestFixer()
        assertTrue(fixer.startInWriteAction())
        assertTrue(fixer.familyName.isNotBlank())
        assertTrue(fixer.name.isNotBlank())
        assertTrue(fixer.text.isNotBlank())
    }

    @Test
    fun `test get binary expression element at caret`(){
        val code = """
            if password == "SECRET":
                pass
        """.trimIndent()

        val mockCaretModel = mock<CaretModel> {
            on { offset } doReturn 8
        }
        val mockEditor = mock<Editor> {
            on { caretModel } doReturn mockCaretModel
        }

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = UseCompareDigestFixer()
            assertTrue(fixer.isAvailable(project, mockEditor, testFile))
            val el = getBinaryExpressionElementAtCaret(testFile, mockEditor)
            assertNotNull(el)
            assertTrue(el!!.text.contains("password == "))
        }

        verify(mockEditor, Mockito.times(1)).caretModel
        verify(mockCaretModel, Mockito.times(1)).offset
    }

    @Test
    fun `test get new element at caret`(){
        val code = """
            import hashlib
            if password == "SECRET":
                pass
        """.trimIndent()

        val mockCaretModel = mock<CaretModel> {
            on { offset } doReturn 20
        }
        val mockEditor = mock<Editor> {
            on { caretModel } doReturn mockCaretModel
        }

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = UseCompareDigestFixer()
            assertTrue(fixer.isAvailable(project, mockEditor, testFile))
            val oldEl = getBinaryExpressionElementAtCaret(testFile, mockEditor)
            assertNotNull(oldEl)
            val el = fixer.getNewExpressionAtCaret(testFile, project, oldEl!!)
            assertNotNull(el)
            assertTrue(el!!.text.contains("compare_digest"))
        }

        verify(mockEditor, Mockito.times(1)).caretModel
        verify(mockCaretModel, Mockito.times(1)).offset
    }

    @Test
    fun `test get new element at caret with no imports`(){
        val code = """
            if password == "SECRET":
                pass
        """.trimIndent()

        val mockCaretModel = mock<CaretModel> {
            on { offset } doReturn 8
        }
        val mockEditor = mock<Editor> {
            on { caretModel } doReturn mockCaretModel
        }

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = UseCompareDigestFixer()
            assertTrue(fixer.isAvailable(project, mockEditor, testFile))
            val oldEl = getBinaryExpressionElementAtCaret(testFile, mockEditor)
            assertNotNull(oldEl)
            val el = fixer.getNewExpressionAtCaret(testFile, project, oldEl!!)
            assertNotNull(el)
            assertTrue(el!!.text.contains("compare_digest"))
        }

        verify(mockEditor, Mockito.times(1)).caretModel
        verify(mockCaretModel, Mockito.times(1)).offset
    }

    @Test
    fun `test batch fix`(){
        val code = """
            if password == "SECRET":
                pass
            if password == "SECRET":
                pass
        """.trimIndent()

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = UseCompareDigestFixer()
            val expr: MutableCollection<PyBinaryExpression> = PsiTreeUtil.findChildrenOfType(testFile, PyBinaryExpression::class.java)
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