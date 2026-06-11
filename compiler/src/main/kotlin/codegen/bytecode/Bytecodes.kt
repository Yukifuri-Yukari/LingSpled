package yukifuri.lang.lingspled.compiler.codegen.bytecode

/**
 * All the notes will be written in specification.
 */
object Bytecodes {
    // Bytecodes
    const val NOP: Byte = 0x00 // 0

    const val PUSH8 : Byte = 0x01 // 1
    const val PUSH16: Byte = 0x02 // 2
    const val PUSH32: Byte = 0x03 // 3
    const val PUSH64: Byte = 0x04 // 4
    const val POP   : Byte = 0x05 // 5
    const val WPOP  : Byte = 0x06 // 6
    const val APOP  : Byte = 0x07 // 7
    const val DUP   : Byte = 0x08 // 8
    const val WDUP  : Byte = 0x09 // 9
    const val ADUP  : Byte = 0x0A // 10
    const val NDUPX1: Byte = 0x0B // 11
    const val ADUPX1: Byte = 0x0C // 12
    const val SWAP  : Byte = 0x0D // 13
    const val WSWAP : Byte = 0x0E // 14
    const val ASWAP : Byte = 0x0F // 15

    const val ADD : Byte = 0x10 // 16
    const val SUB : Byte = 0x11 // 17
    const val MUL : Byte = 0x12 // 18
    const val DIV : Byte = 0x13 // 19
    const val MOD : Byte = 0x14 // 20
    const val WADD: Byte = 0x15 // 21
    const val WSUB: Byte = 0x16 // 22
    const val WMUL: Byte = 0x17 // 23
    const val WDIV: Byte = 0x18 // 24
    const val WMOD: Byte = 0x19 // 25
    const val FADD: Byte = 0x1A // 26
    const val FSUB: Byte = 0x1B // 27
    const val FMUL: Byte = 0x1C // 28
    const val FDIV: Byte = 0x1D // 29
    const val FMOD: Byte = 0x1E // 30
    const val DADD: Byte = 0x1F // 31
    const val DSUB: Byte = 0x20 // 32
    const val DMUL: Byte = 0x21 // 33
    const val DDIV: Byte = 0x22 // 34
    const val DMOD: Byte = 0x23 // 35
    const val NEG : Byte = 0x24 // 36
    const val WNEG: Byte = 0x25 // 37
    const val FNEG: Byte = 0x26 // 38
    const val DNEG: Byte = 0x27 // 39

    const val SHL  : Byte = 0x28 // 40
    const val SHR  : Byte = 0x29 // 41
    const val BAND : Byte = 0x2A // 42
    const val BOR  : Byte = 0x2B // 43
    const val XOR  : Byte = 0x2C // 44
    const val BNOT : Byte = 0x2D // 45
    const val USHR : Byte = 0x2E // 46
    const val WSHL : Byte = 0x2F // 47
    const val WSHR : Byte = 0x30 // 48
    const val WBAND: Byte = 0x31 // 49
    const val WBOR : Byte = 0x32 // 50
    const val WXOR : Byte = 0x33 // 51
    const val WBNOT: Byte = 0x34 // 52
    const val WUSHR: Byte = 0x35 // 53
    const val NOT  : Byte = 0x36 // 54
    const val ROTL : Byte = 0x37 // 55
    const val ROTR : Byte = 0x38 // 56

    const val CMPEQ : Byte = 0x39 // 57
    const val CMPNE : Byte = 0x3A // 58
    const val CMPLT : Byte = 0x3B // 59
    const val CMPLE : Byte = 0x3C // 60
    const val CMPGT : Byte = 0x3D // 61
    const val CMPGE : Byte = 0x3E // 62
    const val WCMPEQ: Byte = 0x3F // 63
    const val WCMPNE: Byte = 0x40 // 64
    const val WCMPLT: Byte = 0x41 // 65
    const val WCMPLE: Byte = 0x42 // 66
    const val WCMPGT: Byte = 0x43 // 67
    const val WCMPGE: Byte = 0x44 // 68
    const val FCMPL : Byte = 0x45 // 69
    const val FCMPG : Byte = 0x46 // 70
    const val DCMPL : Byte = 0x47 // 71
    const val DCMPG : Byte = 0x48 // 72
    const val ACMPEQ: Byte = 0x49 // 73
    const val ACMPNE: Byte = 0x4A // 74

    const val NEW       : Byte = 0x4B // 75
    const val CHECKNUL  : Byte = 0x4C // 76
    const val PUSHNUL   : Byte = 0x4D // 77
    const val LDFIELD   : Byte = 0x4E // 78
    const val STFIELD   : Byte = 0x4F // 79
    const val GETCLASS  : Byte = 0x50 // 80
    const val CAST      : Byte = 0x51 // 81
    const val ISINSTANCE: Byte = 0x52 // 82
    const val INVOKESPEC: Byte = 0x53 // 83
    const val INVOKESUPR: Byte = 0x54 // 84
    const val INVOKEINST: Byte = 0x55 // 85
    const val INVOKESTAT: Byte = 0x56 // 86
    const val CALL      : Byte = 0x57 // 87
    const val SYNCBEGIN : Byte = 0x58 // 88
    const val SYNCEND   : Byte = 0x59 // 89
    const val SYNCREF   : Byte = 0x5A // 90

    const val JUMP      : Byte = 0x5B // 91
    const val WJUMP     : Byte = 0x5C // 92
    const val JEQ       : Byte = 0x5D // 93
    const val JNE       : Byte = 0x5E // 94
    const val JLT       : Byte = 0x5F // 95
    const val JLE       : Byte = 0x60 // 96
    const val JGT       : Byte = 0x61 // 97
    const val JGE       : Byte = 0x62 // 98
    const val JNUL      : Byte = 0x63 // 99
    const val JNNUL     : Byte = 0x64 // 100
    const val WHENTABLE : Byte = 0x65 // 101
    const val WHENLOOKUP: Byte = 0x66 // 102
    const val THROW     : Byte = 0x67 // 103
    const val RETURN    : Byte = 0x68 // 104
    const val WRETURN   : Byte = 0x69 // 105
    const val ARETURN   : Byte = 0x6A // 106
    const val RETURNV   : Byte = 0x6B // 107

    const val LOAD  : Byte = 0x6C // 108
    const val STORE : Byte = 0x6D // 109
    const val WLOAD : Byte = 0x6E // 110
    const val WSTORE: Byte = 0x6F // 111
    const val ALOAD : Byte = 0x70 // 112
    const val ASTORE: Byte = 0x71 // 113

    const val NARRNEW: Byte = 0x72 // 114
    const val WARRNEW: Byte = 0x73 // 115
    const val AARRNEW: Byte = 0x74 // 116
    const val ARRLEN : Byte = 0x75 // 117
    const val NARRLOD: Byte = 0x76 // 118
    const val NARRSTR: Byte = 0x77 // 119
    const val WARRLOD: Byte = 0x78 // 120
    const val WARRSTR: Byte = 0x79 // 121
    const val AARRLOD: Byte = 0x7A // 122
    const val AARRSTR: Byte = 0x7B // 123

    const val LDC : Byte = 0x7C // 124
    const val LDC2: Byte = 0x7D // 125
    const val N2W : Byte = 0x7E // 126
    const val W2N : Byte = 0x7F // 127
    const val N2F : Byte = -0x80 // 0x80 (128, -128)
    const val F2N : Byte = -0x7F // 0x81 (129, -127)
    const val N2D : Byte = -0x7E // 0x82 (130, -126)
    const val D2N : Byte = -0x7D // 0x83 (131, -125)
    const val W2F : Byte = -0x7C // 0x84 (132, -124)
    const val F2W : Byte = -0x7B // 0x85 (133, -123)
    const val W2D : Byte = -0x7A // 0x86 (134, -122)
    const val D2W : Byte = -0x79 // 0x87 (135, -121)
    const val F2D : Byte = -0x78 // 0x88 (136, -120)
    const val D2F : Byte = -0x77 // 0x89 (137, -119)

    const val LINENUM: Byte = -0x76 // 0x8A (138, -118)

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

    val opcodes = bytecodes
        .filter { it != "_" }
        .mapIndexed { index, name -> name to if (index >= 128) index - 256 else index }
        .toMap()

    fun generate() {
        prints("Bytecodes", bytecodes)
        println(opcodes)
    }

    fun prints(mess: String, list: List<String>) {
        fun prt(name: String, pad: Int, index: Int) {
            val name = name.replace(" ", "_")
            val hex = index.toString(16).uppercase().padStart(2, '0')
            val literal = if (index <= 127)
                "0x$hex // $index"
            else {
                val neg = (256 - index).toString(16).uppercase().padStart(2, '0')
                "-0x$neg // 0x$hex ($index, ${index - 256})"
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