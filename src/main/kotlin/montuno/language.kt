package montuno

import com.oracle.truffle.api.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source
import montuno.interpreter.*
import montuno.syntax.*
import montuno.truffle.PureCompiler
import montuno.truffle.TruffleCompiler
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@TruffleLanguage.Registration(
    id = MontunoTruffle.LANGUAGE_ID,
    name = "Montuno",
    version = "0.1",
    interactive = true,
    internal = false,
    defaultMimeType = MontunoTruffle.MIME_TYPE,
    characterMimeTypes = [MontunoTruffle.MIME_TYPE],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED,
    fileTypeDetectors = [MontunoDetector::class]
)
class MontunoTruffle : Montuno() {
    override fun getContext(): MontunoContext = MontunoPure.top
    override fun initializeContext(context: MontunoContext) {
        context.top.lang = this
        context.top.ctx = context
        context.compiler = TruffleCompiler(context)
    }
    companion object {
        val lang: MontunoTruffle get() = getCurrentLanguage(MontunoTruffle::class.java)
        val top: MontunoContext get() = getCurrentContext(MontunoTruffle::class.java)
        const val LANGUAGE_ID = "montuno"
        const val MIME_TYPE = "application/x-montuno"
    }
}

@TruffleLanguage.Registration(
    id = MontunoPure.LANGUAGE_ID,
    name = "MontunoPure",
    version = "0.1",
    interactive = true,
    internal = false,
    defaultMimeType = MontunoPure.MIME_TYPE,
    characterMimeTypes = [MontunoPure.MIME_TYPE],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED,
)
class MontunoPure : Montuno() {
    override fun getContext(): MontunoContext = top
    override fun initializeContext(context: MontunoContext) {
        context.top.lang = this
        context.top.ctx = context
        context.compiler = PureCompiler(context)
    }
    companion object {
        val lang: MontunoPure get() = getCurrentLanguage(MontunoPure::class.java)
        val top: MontunoContext get() = getCurrentContext(MontunoPure::class.java)
        const val LANGUAGE_ID = "montuno-pure"
        const val MIME_TYPE = "application/x-montuno-pure"
    }
}

abstract class Montuno : TruffleLanguage<MontunoContext>() {
    abstract fun getContext(): MontunoContext
    override fun createContext(env: Env): MontunoContext = MontunoContext(env)
    override fun isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean) = true
    override fun isObjectOfLanguage(obj: Any): Boolean = false
    override fun getScope(ctx: MontunoContext) = ctx.top
    override fun parse(request: ParsingRequest): CallTarget {
        if (request.argumentNames.isEmpty()) {
            return parse(request.source)
        }
        return parseInline(request.source.characters.toString(), request.argumentNames)
    }
    private fun parseInline(source: String, argNames: List<String>): CallTarget {
        CompilerAsserts.neverPartOfCompilation()
        val src = "λ ${argNames.joinToString(separator=" ")}.$source"
        val root = ProgramRootNode(this, getCurrentContext(this.javaClass), parsePreSyntax(src))
        return Truffle.getRuntime().createCallTarget(root)
    }
    fun parse(source: Source): CallTarget {
        CompilerAsserts.neverPartOfCompilation()
        val root = ProgramRootNode(this, getCurrentContext(this.javaClass), parsePreSyntax(source))
        return Truffle.getRuntime().createCallTarget(root)
    }
}

class ProgramRootNode(l: TruffleLanguage<*>, val top: MontunoContext, private val pre: List<TopLevel>) : RootNode(l) {
    override fun isCloningAllowed() = true
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any? {
        CompilerAsserts.neverPartOfCompilation()
        var res: Any? = null
        val ctx = top.makeLocalContext()
        for (e in pre) {
            top.loc = e.loc
            when (e) {
                is RReset -> top.reset()
                is RPrint -> top.printElaborated()
                is RDecl -> checkDeclaration(top, e.loc, e.n, e.ty)
                is RDefn -> checkDefinition(top, e.loc, e.n, e.ty, e.tm)
                is RBuiltin -> top.registerBuiltins(e.loc, e.ids)
                is RTerm -> {
                    val (a, t) = checkTerm(top, e.tm)
                    res = ctx.eval(a)
                }
                is RCommand -> {
                    if (e.cmd == Pragma.PARSE) {
                        println(e.tm.toString())
                        continue
                    }
                    val (tm, ty) = ctx.infer(MetaInsertion.No, e.tm)
                    println(when (e.cmd) {
                        Pragma.RAW -> ctx.eval(tm)
                        Pragma.RAW_TYPE -> ty
                        Pragma.PRETTY -> ctx.pretty(ctx.eval(tm).forceMeta().quote(Lvl(0), false))
                        Pragma.NORMAL -> ctx.pretty(ctx.eval(tm).forceUnfold().quote(Lvl(0), true))
                        Pragma.TYPE -> ctx.pretty(ty.forceMeta().quote(Lvl(0), false))
                        Pragma.NORMAL_TYPE -> ctx.pretty(ty.forceUnfold().quote(Lvl(0), true))
                        Pragma.PARSE -> {}
                    })
                }
            }
        }
        return res
    }
}

class MontunoDetector : TruffleFile.FileTypeDetector {
    override fun findEncoding(@Suppress("UNUSED_PARAMETER") file: TruffleFile): Charset = StandardCharsets.UTF_8
    override fun findMimeType(file: TruffleFile): String? {
        val name = file.name ?: return null
        if (name.endsWith(".mn")) return MontunoTruffle.MIME_TYPE
        try {
            file.newBufferedReader(StandardCharsets.UTF_8).use { fileContent ->
                if ((fileContent.readLine() ?: "").matches("^#!/usr/bin/env montuno".toRegex()))
                    return MontunoTruffle.MIME_TYPE
            }
        } catch (e: IOException) { // ok
        } catch (e: SecurityException) { // ok
        }
        return null
    }
}
