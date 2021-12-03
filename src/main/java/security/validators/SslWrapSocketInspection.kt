package security.validators

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*
import security.Checks
import security.helpers.SecurityVisitor
import security.helpers.calleeMatches
import security.helpers.qualifiedNameMatches
import security.helpers.skipDocstring

class SslWrapSocketInspection : PyInspection() {
    val check = Checks.SslWrapSocketNoVersionCheck

    override fun getStaticDescription(): String? {
        return check.getStaticDescription()
    }

    override fun buildVisitor(holder: ProblemsHolder,
                              isOnTheFly: Boolean,
                              session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

    private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : SecurityVisitor(holder, session) {
        override fun visitPyCallExpression(node: PyCallExpression) {
            if (skipDocstring(node)) return
            if (!calleeMatches(node, "wrap_socket")) return
            if (!qualifiedNameMatches(node, "ssl.wrap_socket", typeEvalContext)) return

            if (node.arguments.isNullOrEmpty())
                holder.registerProblem(node, Checks.SslWrapSocketNoVersionCheck.getDescription())

            val sslVersionArgument = node.getKeywordArgument("ssl_version")

            // Python 3.6+ uses secure default (PROTOCOL_TLS)
            var hasSecureDefaults: Boolean = false

            if (node.containingFile is PyFile)
                if ((node.containingFile as PyFile).languageLevel.isAtLeast(LanguageLevel.PYTHON36))
                    hasSecureDefaults = true

            if (sslVersionArgument == null && !hasSecureDefaults)
                holder.registerProblem(node, Checks.SslWrapSocketNoVersionCheck.getDescription())

            if (sslVersionArgument is PyNoneLiteralExpression)
                holder.registerProblem(node, Checks.SslWrapSocketNoVersionCheck.getDescription())

            if (sslVersionArgument is PyReferenceExpression){
                val qn = sslVersionArgument.asQualifiedName().toString()
                if (listOf(*BadSSLProtocols).contains(qn))
                    holder.registerProblem(node, Checks.SslBadProtocolsCheck.getDescription())
            }
        }
    }
}