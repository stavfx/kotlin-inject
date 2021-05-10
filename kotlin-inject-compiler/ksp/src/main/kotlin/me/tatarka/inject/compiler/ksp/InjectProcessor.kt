package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import me.tatarka.inject.compiler.COMPONENT
import me.tatarka.inject.compiler.ErrorTypeException
import me.tatarka.inject.compiler.FailedToGenerateException
import me.tatarka.inject.compiler.InjectGenerator
import me.tatarka.inject.compiler.Options
import me.tatarka.inject.compiler.Profiler

class InjectProcessor(private val profiler: Profiler? = null) : SymbolProcessor, KSAstProvider {

    private lateinit var options: Options
    private lateinit var codeGenerator: CodeGenerator
    override lateinit var resolver: Resolver
    override lateinit var logger: KSPLogger

    override fun init(
        options: Map<String, String>,
        kotlinVersion: KotlinVersion,
        codeGenerator: CodeGenerator,
        logger: KSPLogger
    ) {
        this.options = Options.from(options)
        this.codeGenerator = codeGenerator
        this.logger = logger
    }

    @Suppress("LoopWithTooManyJumpStatements")
    override fun process(resolver: Resolver): List<KSAnnotated> {
        this.resolver = resolver

        profiler?.onStart()

        val generator = InjectGenerator(this, options)

        val (elements, failedToValidate) = resolver.getSymbolsWithClassAnnotation(
            COMPONENT.packageName,
            COMPONENT.simpleName
        ).partition { it.validate() }

        for (element in elements) {
            val astClass = element.toAstClass()

            try {
                val file = generator.generate(astClass)
                file.writeTo(codeGenerator)
            } catch (e: FailedToGenerateException) {
                error(e.message.orEmpty(), e.element)
                // Continue so we can see all errors
                continue
            }
        }

        profiler?.onStop()

        logger.warn("failed to process: ${failedToValidate.joinToString(", ")}")

        return failedToValidate
    }

    override fun finish() {
        // ignore
    }
}