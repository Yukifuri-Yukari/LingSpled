package yukifuri.lang.lingspled.compiler.codegen

class ConstantPool {

    sealed class Entry
    data class CStr  (val v: String)                      : Entry()
    data class CFloat(val v: Float)                       : Entry()
    data class CDbl  (val v: Double)                      : Entry()
    data class CInt  (val v: Int)                         : Entry()
    data class CLong (val v: Long)                        : Entry()
    data class CFunc (val name: String, val sig: String)  : Entry()

    private val list = mutableListOf<Entry>()
    private val idx  = mutableMapOf<Entry, Int>()
    private val intUsage  = mutableMapOf<Int,  Int>()
    private val longUsage = mutableMapOf<Long, Int>()

    fun countInt (v: Int)  { intUsage [v] = (intUsage [v] ?: 0) + 1 }
    fun countLong(v: Long) { longUsage[v] = (longUsage[v] ?: 0) + 1 }
    
    fun shouldPoolInt (v: Int)  = v !in Byte.MIN_VALUE..Short.MAX_VALUE && (intUsage [v] ?: 0) >= POOL_THRESHOLD
    fun shouldPoolLong(v: Long) = (longUsage[v] ?: 0) >= POOL_THRESHOLD

    private fun intern(e: Entry): Int = idx.getOrPut(e) { list.size.also { list.add(e) } }

    fun str  (v: String)                 = intern(CStr(v))
    fun float(v: Float)                  = intern(CFloat(v))
    fun dbl  (v: Double)                 = intern(CDbl(v))
    fun int  (v: Int)                    = intern(CInt(v))
    fun long (v: Long)                   = intern(CLong(v))
    fun func (name: String, sig: String) = intern(CFunc(name, sig))

    fun entries(): List<Entry> = list

    companion object {
        const val POOL_THRESHOLD = 2
    }
}