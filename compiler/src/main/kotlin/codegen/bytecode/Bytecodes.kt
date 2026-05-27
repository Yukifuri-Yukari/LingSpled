package yukifuri.lang.lingspled.compiler.codegen.bytecode

/**
 * All the notes will be written in specification.
 */
object Bytecodes {
    // Bytecodes
    const val NOP: Byte = 0x00

    const val PUSH8: Byte = 0x01
    const val PUSH16: Byte = 0x02
    const val PUSH32: Byte = 0x03
    const val PUSH64: Byte = 0x04
    const val POP: Byte = 0x05
    const val WPOP: Byte = 0x06
    const val APOP: Byte = 0x07
    const val DUP: Byte = 0x08
    const val WDUP: Byte = 0x09
    const val ADUP: Byte = 0x0A
    const val NDUPX1: Byte = 0x0B
    const val ADUPX1: Byte = 0x0C
    const val SWAP: Byte = 0x0D
    const val WSWAP: Byte = 0x0E
    const val ASWAP: Byte = 0x0F

    const val ADD: Byte = 0x10
    const val SUB: Byte = 0x11
    const val MUL: Byte = 0x12
    const val DIV: Byte = 0x13
    const val MOD: Byte = 0x14
    const val WADD: Byte = 0x15
    const val WSUB: Byte = 0x16
    const val WMUL: Byte = 0x17
    const val WDIV: Byte = 0x18
    const val WMOD: Byte = 0x19
    const val FADD: Byte = 0x1A
    const val FSUB: Byte = 0x1B
    const val FMUL: Byte = 0x1C
    const val FDIV: Byte = 0x1D
    const val FMOD: Byte = 0x1E
    const val DADD: Byte = 0x1F
    const val DSUB: Byte = 0x20
    const val DMUL: Byte = 0x21
    const val DDIV: Byte = 0x22
    const val DMOD: Byte = 0x23
    const val NEG: Byte = 0x24
    const val WNEG: Byte = 0x25
    const val FNEG: Byte = 0x26
    const val DNEG: Byte = 0x27

    const val SHL: Byte = 0x28
    const val SHR: Byte = 0x29
    const val BAND: Byte = 0x2A
    const val BOR: Byte = 0x2B
    const val XOR: Byte = 0x2C
    const val BNOT: Byte = 0x2D
    const val USHR: Byte = 0x2E
    const val WSHL: Byte = 0x2F
    const val WSHR: Byte = 0x30
    const val WBAND: Byte = 0x31
    const val WBOR: Byte = 0x32
    const val WXOR: Byte = 0x33
    const val WBNOT: Byte = 0x34
    const val WUSHR: Byte = 0x35
    const val NOT: Byte = 0x36
    const val ROTL: Byte = 0x37
    const val ROTR: Byte = 0x38

    const val CMPEQ: Byte = 0x39
    const val CMPNE: Byte = 0x3A
    const val CMPLT: Byte = 0x3B
    const val CMPLE: Byte = 0x3C
    const val CMPGT: Byte = 0x3D
    const val CMPGE: Byte = 0x3E
    const val WCMPEQ: Byte = 0x3F
    const val WCMPNE: Byte = 0x40
    const val WCMPLT: Byte = 0x41
    const val WCMPLE: Byte = 0x42
    const val WCMPGT: Byte = 0x43
    const val WCMPGE: Byte = 0x44
    const val FCMPL: Byte = 0x45
    const val FCMPG: Byte = 0x46
    const val DCMPL: Byte = 0x47
    const val DCMPG: Byte = 0x48
    const val ACMPEQ: Byte = 0x49
    const val ACMPNE: Byte = 0x4A

    const val NEW: Byte = 0x4B
    const val CHECKNUL: Byte = 0x4C
    const val PUSHNUL: Byte = 0x4D
    const val LDFIELD: Byte = 0x4E
    const val STFIELD: Byte = 0x4F
    const val GETCLASS: Byte = 0x50
    const val CAST: Byte = 0x51
    const val ISINSTANCE: Byte = 0x52
    const val INVOKESPEC: Byte = 0x53
    const val INVOKESUPR: Byte = 0x54
    const val INVOKEINST: Byte = 0x55
    const val INVOKESTAT: Byte = 0x56
    const val CALL: Byte = 0x57
    const val SYNCBEGIN: Byte = 0x58
    const val SYNCEND: Byte = 0x59
    const val SYNCREF: Byte = 0x5A

    const val JUMP: Byte = 0x5B
    const val WJUMP: Byte = 0x5C
    const val JEQ: Byte = 0x5D
    const val JNE: Byte = 0x5E
    const val JLT: Byte = 0x5F
    const val JLE: Byte = 0x60
    const val JGT: Byte = 0x61
    const val JGE: Byte = 0x62
    const val JNUL: Byte = 0x63
    const val JNNUL: Byte = 0x64
    const val WHENTABLE: Byte = 0x65
    const val WHENLOOKUP: Byte = 0x66
    const val THROW: Byte = 0x67
    const val RETURN: Byte = 0x68
    const val WRETURN: Byte = 0x69
    const val ARETURN: Byte = 0x6A
    const val RETURNV: Byte = 0x6B

    const val LOAD: Byte = 0x6C
    const val STORE: Byte = 0x6D
    const val WLOAD: Byte = 0x6E
    const val WSTORE: Byte = 0x6F
    const val ALOAD: Byte = 0x70
    const val ASTORE: Byte = 0x71

    const val NARRNEW: Byte = 0x72
    const val WARRNEW: Byte = 0x73
    const val AARRNEW: Byte = 0x74
    const val ARRLEN: Byte = 0x75
    const val NARRLOD: Byte = 0x76
    const val NARRSTR: Byte = 0x77
    const val WARRLOD: Byte = 0x78
    const val WARRSTR: Byte = 0x79
    const val AARRLOD: Byte = 0x7A
    const val AARRSTR: Byte = 0x7B

    const val LDC: Byte = 0x7C
    const val LDC2: Byte = 0x7D
    const val N2W: Byte = 0x7E
    const val W2N: Byte = 0x7F
    const val N2F: Byte = -0x80 // 0x80
    const val F2N: Byte = -0x7F // 0x81
    const val N2D: Byte = -0x7E // 0x82
    const val D2N: Byte = -0x7D // 0x83
    const val W2F: Byte = -0x7C // 0x84
    const val F2W: Byte = -0x7B // 0x85
    const val W2D: Byte = -0x7A // 0x86
    const val D2W: Byte = -0x79 // 0x87
    const val F2D: Byte = -0x78 // 0x88
    const val D2F: Byte = -0x77 // 0x89

    const val LINENUM: Byte = -0x76 // 0x8A

    val bytecodes = listOf(
        "nop", "_",
        // 基本 栈操作系列指令
        "push8", "push16", "push32", "push64",
        "pop", "wpop", "apop",
        "dup", "wdup", "adup",
        "ndupx1", "adupx1", // ..., b, a → ..., a, b, a（narrow/ref 拷贝插入次栈顶之下）
        "swap", "wswap", "aswap",
        "_",
        // 基本 运算系列指令
        "add", "sub", "mul", "div", "mod",
        "wadd", "wsub", "wmul", "wdiv", "wmod",
        "fadd", "fsub", "fmul", "fdiv", "fmod",
        "dadd", "dsub", "dmul", "ddiv", "dmod",
        "neg", "wneg", "fneg", "dneg",
        "_",
        // 位 / 逻辑 运算系列指令
        "shl", "shr", "band", "bor", "xor", "bnot", "ushr",
        "wshl", "wshr", "wband", "wbor", "wxor", "wbnot", "wushr",
        "not",
        "rotl", "rotr",
        "_",
        // 比较（弹出两个操作数，将 boolean 结果 0/1 推入栈）
        // narrow (Int/Short/Byte/Boolean)
        "cmpeq", "cmpne", "cmplt", "cmple", "cmpgt", "cmpge",
        // wide (Long)
        "wcmpeq", "wcmpne", "wcmplt", "wcmple", "wcmpgt", "wcmpge",
        // float: 弹出两个 float，推入 Int(-1/0/1)；NaN 时 fcmpl 推 -1，fcmpg 推 +1
        "fcmpl", "fcmpg",
        // double: 同 float
        "dcmpl", "dcmpg",
        // reference: 指针相等比较（用于 === 和 null 判断），推 boolean 结果
        "acmpeq", "acmpne",
        "_",
        // 对象 / 函数 系列指令
        "new",
        "checknul", "pushnul",
        "ldfield", "stfield",
        "getclass", "cast", "isinstance",
        "invokespec", "invokesupr", "invokeinst", "invokestat",
        /**
         * call: 从当前文件的函数表按 2 字节索引调用函数。
         * invokeinst 等通过方法名（常量池索引）在对象上分派。
         * Lambda/函数对象通过 ldfield + invokeinst "invoke" 调用。
         */
        "call",
        "syncbegin", "syncend", // 获取 / 释放当前 syncref 槽中保存的监视器
        "syncref",              // 弹出引用，存入隐式 sync 槽（不获取监视器）
        "_",
        // 控制流（均使用 Relative-16 有符号偏移，wjump 使用 Relative-32）
        "jump", "wjump",
        "jeq", "jne", "jlt", "jle", "jgt", "jge", // 弹出一个 Int，与 0 比较后跳转
        "jnul", "jnnul",
        "whentable",  // 操作数：default(2B) low(4B) high(4B) + (high-low+1) 个 Relative-16 偏移
        "whenlookup", // 操作数：default(2B) base(4B) scale(4B) count(2B) + count 个 Relative-16 偏移
        "throw",      // 抛出栈顶引用
        "return", "wreturn", "areturn", "returnv",
        "_",
        // 局部变量
        "load", "store",  // narrow: Int/Float（32-bit）
        "wload", "wstore", // wide: Long/Double（64-bit）
        "aload", "astore", // reference（对象引用，GC 可区分）
        "_",
        // 数组
        "narrnew", "warrnew", "aarrnew", // 创建对应元素类型的数组
        "arrlen",
        "narrlod", "narrstr", // narrow 元素读写
        "warrlod", "warrstr", // wide 元素读写
        "aarrlod", "aarrstr", // reference 元素读写
        "_",
        // 常量池 / 类型转换
        "ldc",          // 从常量池加载 narrow 常量（String/Float/Int）
        "ldc2",         // 从常量池加载 wide 常量（Long/Double）
        "n2w", "w2n",   // Int <-> Long
        "n2f", "f2n",   // Int <-> Float
        "n2d", "d2n",   // Int <-> Double
        "w2f", "f2w",   // Long <-> Float
        "w2d", "d2w",   // Long <-> Double
        "f2d", "d2f",   // Float <-> Double
        "_",
        // 调试信息
        "linenum",      // 操作数：2 字节行号，无栈效应
        "_"
    )

    fun generate() {
        prints("Bytecodes", bytecodes)
    }

    fun prints(mess: String, list: List<String>) {
        fun prt(name: String, pad: Int, index: Int) {
            val name = name.replace(" ", "_")
            val hex = index.toString(16).uppercase().padStart(2, '0')
            val literal = if (index <= 127)
                "0x$hex"
            else {
                val neg = (256 - index).toString(16).uppercase().padStart(2, '0')
                "-0x$neg // 0x$hex"
            }
            println("const val ${name.uppercase()}${" ".repeat(pad - name.length)}: Byte = $literal")
        }

        val ls = list.toMutableList()
        ls.removeAll { it == "_" }
        // Throws exception if there are duplicates
        if (ls.size != ls.toSet().size) {
            throw IllegalArgumentException("There are duplicates in $mess.")
        }

        val outputs = mutableListOf<String>()
        var maxLen = 1
        var i = 0
        println("// $mess")
        for (name in list) {
            if (name == "_") {
                for (output in outputs) {
                    prt(output, maxLen, i++)
                }
                println()
                outputs.clear()
                maxLen = 0
            } else {
                outputs.add(name)
                if (maxLen < name.length)
                    maxLen = name.length
            }
        }
        if (outputs.isNotEmpty()) {
            for (output in outputs) {
                prt(output, maxLen, i++)
            }
            println()
            outputs.clear()
        }
    }
}