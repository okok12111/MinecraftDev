/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2018 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev.util

import com.demonwav.mcdev.translations.identification.TranslationInstance
import com.demonwav.mcdev.translations.identification.TranslationInstance.Companion.FormattingError
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiCall
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPolyadicExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable

fun PsiAnnotationMemberValue.evaluate(defaultValue: String?, parameterReplacement: String?): String? {
    val visited = mutableSetOf<PsiAnnotationMemberValue?>()

    fun eval(expr: PsiAnnotationMemberValue?, defaultValue: String?): String? {
        if (!visited.add(expr)) {
            return defaultValue
        }

        when {
            expr is PsiTypeCastExpression && expr.operand != null ->
                return eval(expr.operand, defaultValue)
            expr is PsiReferenceExpression -> {
                val reference = expr.advancedResolve(false).element
                if (reference is PsiParameter) {
                    return parameterReplacement
                }
                if (reference is PsiVariable && reference.initializer != null) {
                    return eval(reference.initializer, null)
                }
            }
            expr is PsiLiteral ->
                return expr.value.toString()
            expr is PsiPolyadicExpression && expr.operationTokenType == JavaTokenType.PLUS -> {
                var value = ""
                for (operand in expr.operands) {
                    val operandResult = eval(operand, defaultValue) ?: return defaultValue
                    value += operandResult
                }
                return value
            }
        }

        return defaultValue
    }

    return eval(this, defaultValue)
}

fun PsiExpression.substituteParameter(allowReferences: Boolean, allowTranslations: Boolean): String? {
    val visited = mutableSetOf<PsiExpression?>()

    tailrec fun substitute(expr: PsiExpression?, defaultValue: String? = null): String? {
        if (!visited.add(expr) && expr != null) {
            return "\${${expr.text}}"
        }
        when {
            expr is PsiTypeCastExpression && expr.operand != null ->
                return substitute(expr.operand)
            expr is PsiReferenceExpression -> {
                val reference = expr.advancedResolve(false).element
                if (reference is PsiVariable && reference.initializer != null) {
                    return substitute(reference.initializer, "\${${expr.text}}")
                }
            }
            expr is PsiLiteral ->
                return expr.value.toString()
            expr is PsiPolyadicExpression && expr.operationTokenType == JavaTokenType.PLUS -> {
                var value = ""
                for (operand in expr.operands) {
                    val operandResult = operand.evaluate(null, null) ?: return defaultValue
                    value += operandResult
                }
                return value
            }
            expr is PsiCall && allowTranslations ->
                for (argument in expr.argumentList?.expressions ?: emptyArray()) {
                    val translation = TranslationInstance.find(argument) ?: continue
                    if (translation.formattingError == FormattingError.MISSING) {
                        return "{ERROR: Missing formatting arguments for '${translation.text}'}"
                    }
                    return translation.text
                }
        }
        return if (allowReferences && expr != null) {
            "\${${expr.text}}"
        } else {
            defaultValue
        }
    }

    return substitute(this)
}
