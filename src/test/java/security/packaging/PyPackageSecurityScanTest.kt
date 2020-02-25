package security.packaging

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import security.SecurityTestTask
import java.io.StringReader

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PyPackageSecurityScanTest: SecurityTestTask() {
    lateinit var instance: SafetyDbChecker

    @BeforeAll
    override fun setUp() {
        val testLookupData = """
            {
             "apples": [
                "<0.6.0"
                ],
             "bananas": [
                "<1.0.0,>=0.5.0"
             ]
            }
        """.trimIndent()
        val testData = """
            {
             "apples": [
                 {
                    "advisory": "apple pips taste nasty.",
                    "cve": null,
                    "id": "pyup.io-25612",
                    "specs": [
                        "<0.6.0"
                    ],
                    "v": "<0.6.0"
                }
                ],
              "bananas": [
                 {
                    "advisory": "green bananas give you stomach ache.",
                    "cve": "CVE-1234",
                    "id": "pyup.io-253",
                    "specs": [
                        "<1.0.0,>=0.5.0"
                    ],
                    "v": "<1.0.0,>=0.5.0"
                }
                ]
            }
        """.trimIndent()
        val lookupReader = StringReader(testLookupData)
        val databaseReader = StringReader(testData)
        this.instance = SafetyDbChecker(lookupReader = lookupReader, databaseReader = databaseReader)
        super.setUp()
    }

    @AfterAll
    override fun tearDown(){
        super.tearDown()
    }

    @Test
    fun `test no python sdk raises info message`(){
        assertNull(PyPackageSecurityScan.checkPackages(project))
    }

    @Test
    fun `test check packages`(){
        val mockSdk = mock<Sdk> {
            on { name } doReturn ("test")
            on { homePath } doReturn (".")
        }
        assertEquals(PyPackageSecurityScan.checkPackagesInSdks(setOf(mockSdk), project, instance), 0)
    }

    @Test
    fun `test null packages`(){
        val mockPackageManager = mock<PyPackageManager> {
            on { packages } doReturn(null)
        }
        assertNull(PyPackageSecurityScan.inspectLocalPackages(mockPackageManager, project, instance))
    }

    @Test
    fun `test no packages`(){
        val mockPackageManager = mock<PyPackageManager> {
            on { packages } doReturn(listOf())
        }
        assertEquals(PyPackageSecurityScan.inspectLocalPackages(mockPackageManager, project, instance), 0)
    }

    @Test
    fun `test ok packages`(){
        val testPackage1 = mock<PyPackage> {
            on { name } doReturn "good"
            on { version } doReturn "0.4.0"
        }
        val mockPackageManager = mock<PyPackageManager> {
            on { packages } doReturn(listOf(testPackage1))
        }
        assertEquals(PyPackageSecurityScan.inspectLocalPackages(mockPackageManager, project, instance), 0)
        verify(testPackage1, times(1)).name
        verify(mockPackageManager, times(2)).packages
    }

    @Test
    fun `test bad packages`(){
        val testPackage1 = mock<PyPackage> {
            on { name } doReturn "apples"
            on { toString() } doReturn "apples"
            on { version } doReturn "0.4.0"
        }
        val testPackage2 = mock<PyPackage> {
            on { name } doReturn "bananas"
            on { toString() } doReturn "bananas"
            on { version } doReturn "0.6.0"
        }
        val mockPackageManager = mock<PyPackageManager> {
            on { packages } doReturn(listOf(testPackage1, testPackage2))
        }
        assertEquals(PyPackageSecurityScan.inspectLocalPackages(mockPackageManager, project, instance), 2)
        verify(testPackage1, times(2)).name
        verify(mockPackageManager, times(2)).packages
    }

    @Test
    fun `test render renderMessage with null cve record`(){
        val record = SafetyDbChecker.SafetyDbRecord("Test is bad", null, "xyz", listOf("<= 1.0.0"), "<= 1.0.0")
        val message = PyPackageSecurityScan.renderMessage(record)
        assertFalse(message.isEmpty())
    }

    @Test
    fun `test render renderMessage with valid cve record`(){
        val record = SafetyDbChecker.SafetyDbRecord("Test is bad", "CVE-2020-123.3", "xyz", listOf("<= 1.0.0"), "<= 1.0.0")
        val message = PyPackageSecurityScan.renderMessage(record)
        assertFalse(message.isEmpty())
    }
}