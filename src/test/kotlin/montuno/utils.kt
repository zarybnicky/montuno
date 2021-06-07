package montuno

import org.graalvm.polyglot.Context

fun makeCtx(): Context = Context.newBuilder().allowExperimentalOptions(true).build()