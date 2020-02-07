package security.packaging

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.pyRequirementVersionSpec
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import java.io.Reader
import java.util.stream.Collectors
import java.util.stream.StreamSupport


class SafetyDbChecker {
    private lateinit var database: Map<String?, List<SafetyDbRecord>>
    private lateinit var lookup: Map<String?, List<String>>

    private val tripleRequirementMap: HashMap<String, PyRequirementRelation> = hashMapOf(
            "===" to PyRequirementRelation.STR_EQ)
    private val doubleRequirementMap: HashMap<String, PyRequirementRelation> = hashMapOf(
            "==" to PyRequirementRelation.EQ,
            "<=" to PyRequirementRelation.LTE,
            ">=" to  PyRequirementRelation.GTE,
            "~=" to PyRequirementRelation.COMPATIBLE,
            "!=" to PyRequirementRelation.NE)
    private val singleRequirementMap: HashMap<String, PyRequirementRelation> = hashMapOf(
            "<" to PyRequirementRelation.LT,
            ">" to PyRequirementRelation.GT)

    data class SafetyDbRecord(
        val advisory: String,
        val cve: String?,
        val id: String,
        val specs: List<String>,
        val v: String
    )

    constructor() {
        load(
            this.javaClass.classLoader.getResourceAsStream("safety-db/insecure_full.json").reader(),
            this.javaClass.classLoader.getResourceAsStream("safety-db/insecure.json").reader())
    }
    constructor (databaseReader: Reader, lookupReader: Reader ) {
        load(databaseReader, lookupReader)
    }

    private fun load(databaseReader: Reader, lookupReader: Reader) {
        val recordLookupType = object : TypeToken<Map<String?, List<String>>>() {}.type
        lookup = Gson().fromJson<Map<String?, List<String>>>(lookupReader, recordLookupType)

        val recordDatabaseType = object : TypeToken<Map<String?, List<SafetyDbRecord>>>() {}.type
        database = Gson().fromJson<Map<String?, List<SafetyDbRecord>>>(databaseReader, recordDatabaseType)
    }

    fun hasMatch(pythonPackage: PyPackage): Boolean{
        for (record in lookup[pythonPackage.name] ?: return false){
            val specs = parseVersionSpecs(record) ?: continue
            if (specs.all { it != null && it.matches(pythonPackage.version) })
                return true
        }
        return false
    }

    fun getMatches (pythonPackage: PyPackage): List<SafetyDbRecord> {
        /// Kotlin rewrite of PyRequirementParser.getMatches taking advantage of predicates
        val records: ArrayList<SafetyDbRecord> = ArrayList()
        for (record in database[pythonPackage.name] ?: error("Package not in database")){
            val specs = parseVersionSpecs(record.v) ?: continue
            if (specs.all { it != null && it.matches(pythonPackage.version) })
                records.add(record)
        }
        return records.toList()
    }

    private fun parseVersionSpecs(versionSpecs: String): List<PyRequirementVersionSpec?>? {
        /// Taken from PyRequirementParser, but that function is Private :-(
        return StreamSupport
                .stream(StringUtil.tokenize(versionSpecs, ",").spliterator(), false)
                .map { obj: String -> obj.trim { it <= ' ' } }
                .map { versionSpec: String? -> parseVersionSpec(versionSpec!!) }
                .collect(Collectors.toList())
    }

    private fun parseVersionSpec(versionSpec: String): PyRequirementVersionSpec? {
        val relation: PyRequirementRelation?
        if (tripleRequirementMap.containsKey(versionSpec.substring(0, 3)))
            relation = tripleRequirementMap[versionSpec.substring(0, 3)]
        else if (doubleRequirementMap.containsKey(versionSpec.substring(0, 2)))
            relation = doubleRequirementMap[versionSpec.substring(0, 2)]
        else if (singleRequirementMap.containsKey(versionSpec.substring(0, 1)))
            relation = singleRequirementMap[versionSpec.substring(0, 1)]
        else
            return null

        val versionIndex = findFirstNotWhiteSpaceAfter(versionSpec, relation!!.presentableText.length)
        return pyRequirementVersionSpec(relation, versionSpec.substring(versionIndex))
    }

    fun findFirstNotWhiteSpaceAfter(line: String, beginIndex: Int): Int {
        /// Taken from PyRequirementParser, but that function is Private :-(
        for (i in beginIndex until line.length) {
            if (!StringUtil.isWhiteSpace(line[i])) {
                return i
            }
        }
        return line.length
    }
}