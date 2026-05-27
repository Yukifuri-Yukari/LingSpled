package yukifuri.lang.lingspled.compiler.ir.mir.bytecode

import yukifuri.lang.lingspled.compiler.codegen.ConstantPool

data class FieldDescriptor(
    val name: String,
    val type: String,
    val isConstant: Boolean,
    val accessFlags: Short = 0,
)

data class FunctionCode(
    val name: String,
    val descriptor: String,
    val accessFlags: Short,
    val paramSlots: Int,
    val localSlots: Int,
    val bytecode: ByteArray,
) {
    val slotCount: Int get() = paramSlots + localSlots
    override fun equals(other: Any?) = other is FunctionCode && name == other.name
    override fun hashCode() = name.hashCode()
    override fun toString() = "FunctionCode(name=$name, slots=$slotCount, bytes=${bytecode.size})"
}

data class ClassCode(
    val name: String,
    val superClass: String,
    val isInterface: Boolean,
    val accessFlags: Short = 0,
    val fields: List<FieldDescriptor>,
    val methods: List<FunctionCode>,
)

data class FileCode(
    val functions: List<FunctionCode>,
    val classes: List<ClassCode>,
    val pool: ConstantPool,
)
