package security.validators

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyCallExpression
import security.Checks
import security.helpers.QualifiedNames.getQualifiedName
import security.helpers.SecurityVisitor

class PickleLoadInspection : PyInspection() {
    val check = Checks.PickleLoadCheck

    override fun getStaticDescription(): String? {
        return check.getStaticDescription()
    }

    override fun buildVisitor(holder: ProblemsHolder,
                              isOnTheFly: Boolean,
                              session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

    private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : SecurityVisitor(holder, session) {
        override fun visitPyCallExpression(node: PyCallExpression) {
            val pickleLoadNames = arrayOf("pickle.load", "pickle.loads", "cPickle.load", "cPickle.loads", "pickle._load", "pickle._loads", "cPickle._load", "cPickle._loads")
            node.callee?.name ?: return
            val qualifiedName = getQualifiedName(node) ?: return
            if (!listOf(*pickleLoadNames).contains(qualifiedName)) return
            holder.registerProblem(node, Checks.PickleLoadCheck.getDescription())
        }
    }
}