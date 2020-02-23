package security.validators

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import security.Checks
import security.fixes.DjangoAddMiddlewareFixer
import security.helpers.SecurityVisitor
import security.helpers.skipDocstring

class DjangoMiddlewareInspection : PyInspection() {
    val check = Checks.DjangoClickjackMiddlewareCheck

    override fun getStaticDescription(): String? {
        return check.getStaticDescription()
    }

    override fun buildVisitor(holder: ProblemsHolder,
                              isOnTheFly: Boolean,
                              session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

    private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : SecurityVisitor(holder, session) {
        override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
            if (skipDocstring(node)) return
            if (node.containingFile?.name != "settings.py") return
            val leftExpression = node.leftHandSideExpression?.text ?: return
            if (leftExpression != "MIDDLEWARE") return
            val assignedValue = node.assignedValue ?: return
            if (assignedValue !is PyListLiteralExpression) return
            val middleware = assignedValue.elements.filterIsInstance<PyStringLiteralExpression>().map { it.stringValue }

            if (middleware.contains("django.middleware.csrf.CsrfViewMiddleware").not()) {
                holder.registerProblem(node, Checks.DjangoCsrfMiddlewareCheck.getDescription(), DjangoAddMiddlewareFixer("django.middleware.csrf.CsrfViewMiddleware"))
            }
            if (middleware.contains("django.middleware.clickjacking.XFrameOptionsMiddleware").not()) {
                holder.registerProblem(node, Checks.DjangoClickjackMiddlewareCheck.getDescription(), DjangoAddMiddlewareFixer("django.middleware.clickjacking.XFrameOptionsMiddleware"))
            }
        }
    }
}
