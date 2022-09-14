package io.fluxcapacitor.common.reflection

import java.lang.reflect.Parameter
import kotlin.reflect.KParameter
import kotlin.reflect.full.valueParameters

class KotlinReflectionUtils {
    companion object {
        @JvmStatic
        fun asKotlinParameter(param: Parameter): KParameter {
            val executable = param.declaringExecutable
            val paramIndex = executable.parameters.indexOf(param)
            val owner = executable.declaringClass.kotlin
            val kotlinExecutable = owner.members.find { m -> m.name == executable.name }
            return kotlinExecutable?.valueParameters?.get(paramIndex)!!
        }
    }
}