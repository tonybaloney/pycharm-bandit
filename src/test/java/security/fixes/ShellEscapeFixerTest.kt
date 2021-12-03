package security.fixes

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.*
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
class ShellEscapeFixerTest: SecurityTestTask() {
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
        val fixer = ShellEscapeFixer()
        assertTrue(fixer.startInWriteAction())
        assertTrue(fixer.familyName.isNotBlank())
        assertTrue(fixer.name.isNotBlank())
        assertTrue(fixer.text.isNotBlank())
    }

    @Test
    fun `test get argument at caret`(){
        val code = """
            import subprocess
            subprocess.call(opt, shell=True)
        """.trimIndent()

        val mockCaretModel = mock<CaretModel> {
            on { offset } doReturn 35
        }
        val mockEditor = mock<Editor> {
            on { caretModel } doReturn mockCaretModel
        }

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = ShellEscapeFixer()
            assertTrue(fixer.isAvailable(project, mockEditor, testFile))
            val el = getPyExpressionAtCaret(testFile, mockEditor)
            assertNotNull(el)
            assertTrue(el is PyReferenceExpression)
            assertTrue(el!!.text.contains("opt"))
        }

        verify(mockEditor, Mockito.times(1)).caretModel
        verify(mockCaretModel, Mockito.times(1)).offset
    }

    @Test
    fun `test get argument as list at caret`(){
        val code = """
            import subprocess
            subprocess.call([opt], shell=True)
        """.trimIndent()

        val mockCaretModel = mock<CaretModel> {
            on { offset } doReturn 34
        }
        val mockEditor = mock<Editor> {
            on { caretModel } doReturn mockCaretModel
        }

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = ShellEscapeFixer()
            assertTrue(fixer.isAvailable(project, mockEditor, testFile))
            val el = getPyExpressionAtCaret(testFile, mockEditor)
            assertNotNull(el)
            assertTrue(el is PyListLiteralExpression)
            assertTrue(el!!.text.contains("[opt]"))
        }

        verify(mockEditor, Mockito.times(1)).caretModel
        verify(mockCaretModel, Mockito.times(1)).offset
    }

    @Test
    fun `test get new element at caret escapes ref expr`(){
        val code = """
            import subprocess
            subprocess.call(opt, shell=True)
        """.trimIndent()

        val mockCaretModel = mock<CaretModel> {
            on { offset } doReturn 35
        }
        val mockEditor = mock<Editor> {
            on { caretModel } doReturn mockCaretModel
        }

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = ShellEscapeFixer()
            assertTrue(fixer.isAvailable(project, mockEditor, testFile))
            val old = getPyExpressionAtCaret(testFile, mockEditor)
            val el = fixer.getNewExpressionFromPyExpression(testFile, project, old!!)
            assertNotNull(el)
            assertTrue(el is PyCallExpression)
            assertEquals(el!!.callee?.text, "shlex_quote")
            assertTrue(el.arguments.first() is PyReferenceExpression)
            assertEquals((el.arguments.first() as PyReferenceExpression).text, "opt")
        }

        verify(mockEditor, Mockito.times(1)).caretModel
        verify(mockCaretModel, Mockito.times(1)).offset
    }

    @Test
    fun `test get new element at caret escapes list expr`(){
        val code = """
            import subprocess
            subprocess.call(['ps', opt], shell=True)
        """.trimIndent()

        val mockCaretModel = mock<CaretModel> {
            on { offset } doReturn 34
        }
        val mockEditor = mock<Editor> {
            on { caretModel } doReturn mockCaretModel
        }

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = ShellEscapeFixer()
            assertTrue(fixer.isAvailable(project, mockEditor, testFile))
            val old = getPyExpressionAtCaret(testFile, mockEditor)
            val el = fixer.getNewExpressionFromList(testFile, project, old as PyListLiteralExpression)
            assertNotNull(el)
            assertTrue(el is PyListLiteralExpression)
            assertTrue((el as PyListLiteralExpression).elements.first() is PyStringLiteralExpression)
            assertEquals((el).elements.first().text, "'ps'")
            assertTrue((el).elements[1] is PyCallExpression)
            val secondArg = ((el).elements[1] as PyCallExpression)
            assertEquals(secondArg.callee?.text, "shlex_quote")
            assertTrue(secondArg.arguments.first() is PyReferenceExpression)
            assertEquals((secondArg.arguments.first() as PyReferenceExpression).text, "opt")
        }

        verify(mockEditor, Mockito.times(1)).caretModel
        verify(mockCaretModel, Mockito.times(1)).offset
    }

    @Test
    fun `test batch fix with literal`(){
        val code = """
            import subprocess
            subprocess.call('foo')
            subprocess.call('foo')
        """.trimIndent()

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = ShellEscapeFixer()
            val expr: MutableCollection<PyExpression> = PsiTreeUtil.findChildrenOfType(testFile, PyStringLiteralExpression::class.java)
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

    @Test
    fun `test batch fix with list literal`(){
        val code = """
            import subprocess
            subprocess.call(['foo'])
            subprocess.call(['foo'])
        """.trimIndent()

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = ShellEscapeFixer()
            val expr: MutableCollection<PyListLiteralExpression> = PsiTreeUtil.findChildrenOfType(testFile, PyListLiteralExpression::class.java)
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

    @Test
    fun `test batch fix with list literal of existing escaped instances`(){
        val code = """
            import subprocess
            import shlex
            subprocess.call([shlex.quote('foo')])
            subprocess.call(['foo'])
        """.trimIndent()

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = ShellEscapeFixer()
            val expr: MutableCollection<PyListLiteralExpression> = PsiTreeUtil.findChildrenOfType(testFile, PyListLiteralExpression::class.java)
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

    @Test
    fun `test batch fix with list literal of existing non-escaped instances`(){
        val code = """
            import subprocess
            subprocess.call([get_value_from_place('foo')])
            subprocess.call(['foo'])
        """.trimIndent()

        ApplicationManager.getApplication().runReadAction {
            val testFile = this.createLightFile("app.py", PythonFileType.INSTANCE.language, code)
            assertNotNull(testFile)
            val fixer = ShellEscapeFixer()
            val expr: MutableCollection<PyListLiteralExpression> = PsiTreeUtil.findChildrenOfType(testFile, PyListLiteralExpression::class.java)
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