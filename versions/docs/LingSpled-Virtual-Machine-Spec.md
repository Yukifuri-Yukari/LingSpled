# LingSpled - 虚拟机标准

# 注

如若特别描述, 字节序为小端序 (Little Endian), 槽位序为小端 (宽值低 32 在低编号, 高 32 在高编号)

# 索引

1. [字节码文件格式](#字节码文件格式)
2. [字节码](#字节码)

# 字节码文件格式

| 序号 |   段名   |   大小    |      示例      |         作用          |
|:--:|:------:|:-------:|:------------:|:-------------------:|
| 0  |   魔数   | 8(Byte) | `"LSpled~~"` |       区分文件格式        |
| 1  |   版本   |    8    |  0 (第一个版本)   |       区分编译器版本       |
| 2  | 常量池条目数 |    2    |      n       |      记录常量池条目数量      |
| 3  | 常量池条目  | n (不定长) |      -       |        常量池条目        |
| 4  |  属性数量  |    2    |      n       |      记录顶层属性数量       |
| 5  |  属性条目  | n (不定长) |      -       | 顶层属性 (类 / 函数 / 变量等) |

```
  ┌────────────────────────────────────────┐
  │            Magic Number                │
  │         "LSpled~~" (8 bytes)           │
  ├────────────────────────────────────────┤
  │              Version                   │
  │            (8 bytes)                   │
  ├────────────────────────────────────────┤
  │         Constant Pool Count            │
  │            (2 bytes)                   │
  ├────────────────────────────────────────┤
  │        Constant Pool Entry #0          │
  │           (variable length)            │
  ├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
  │              ...                       │
  ├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
  │        Constant Pool Entry #N-1        │
  ├────────────────────────────────────────┤
  │           Attribute Count              │
  │            (2 bytes)                   │
  ├────────────────────────────────────────┤
  │          Attribute Entry #0            │
  │           (variable length)            │
  ├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
  │              ...                       │
  ├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
  │          Attribute Entry #M-1          │
  ├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
  │            Debug Info                  │
  └────────────────────────────────────────┘
```

## 常量池条目

| 序号 | 段名 | 大小 |    示例     |   作用   |
|:--:|:--:|:--:|:---------:|:------:|
| 0  | 标记 | 1  | 0 (Byte)  | 标记常量类型 |
| 1  | 内容 | n  | 21 (占用 1) |   -    |

### 常量池标记

| 标记值 |         类型         | 内容大小  |             内容格式              |
|:---:|:------------------:|:-----:|:-----------------------------:|
|  0  |      Byte (B)      |   1   |             1 字节值             |
|  1  |     Short (S)      |   2   |             2 字节值             |
|  2  |      Int (I)       |   4   |             4 字节值             |
|  3  |      Long (L)      |   8   |             8 字节值             |
|  4  |     Float (F)      |   4   |         IEEE 754 单精度          |
|  5  |     Double (D)     |   8   |         IEEE 754 双精度          |
|  6  |       String       | 1+2+n |      编码(1) + 字符数(2) + 内容      |
|  7  | FunctionDescriptor |  2+2  | nameIdx(2) + descriptorIdx(2) |
|  8  |    Class (R...)    |   2   |       nameIdx(2) 指向字符串        |

### String 条目详解

编码字节含义：`0x00` = Latin-1 (ISO 8859-1)，`0x01` = UTF-16LE。  
字符数为 `char` 单元数（代理对计 2）。Latin-1 时每字符占 1 字节，UTF-16 时每字符占 2 字节。

### FunctionDescriptor 条目详解

- `nameIdx`（2 字节）：指向方法/函数名称的 CString 条目索引
- `descriptorIdx`（2 字节）：指向描述符字符串的 CString 条目索引

描述符格式同 JVM，但使用 LingSpled 类型字符：`(<参数类型...>)<返回类型>`  
类型字符映射（见 `LType.descriptorChar`）：

| LingSpled 类型 |            字符             |              示例              |
|:------------:|:-------------------------:|:----------------------------:|
|     Byte     |            `B`            |                              |
|    Short     |            `S`            |                              |
|     Int      |            `I`            |                              |
|     Long     |            `L`            |                              |
|    Float     |            `F`            |                              |
|    Double    |            `D`            |                              |
|   Boolean    |            `O`            |                              |
|    String    |  `Rling/std/String;`（FQN）  | 不再使用单字符 `N`                  |
|     Any      |  `Rling/std/Any;`（FQN）    | 不再使用单字符 `A`                  |
|     Unit     |            `V`            |                              |
|  类引用（含 FQN）  | `R<root>/<pkg>/Name<TP>;` | 以 `R` 开头，`;` 结尾，包路径用 `/` 分隔  |

示例：`fun add(a: Int, b: Int): Unit` → 描述符 `(II)V`  
示例：`fun greet(name: String): String` → 描述符 `(N)N`  
示例：`fun id(x: Any): Any` → 描述符 `(Rling/std/Any;)Rling/std/Any;`

## 全限定名（FQN）格式

LingSpled 中所有跨文件引用均使用全限定名，以避免不同包中同名符号冲突。

### 类 / 类型 FQN

```
R<rootPackage>/<subPackage>/.../ClassName[<TP1,TP2:UpperBound,...>];
```

- 包路径各段之间用 `/` 分隔
- 类名紧跟最后一个 `/` 之后
- 若有泛型实参，以 `<...>` 跟在类名之后，实参之间用 `,` 分隔
- 以 `;` 结尾

#### 类型参数（Type Parameter）与类型实参（Type Argument）区别

| 上下文 | 格式 | 说明 |
|:------|:----|:----|
| 类/函数**声明**中的类型参数 | `<T>` / `<T,R:Rling/std/Any;>` | 裸名；若有上界则用 `:` 跟 FQN 描述符 |
| 描述符中的具体类型**实参** | 完整描述符（`I`、`Rling/std/Any;`…） | 与普通类型描述符相同 |
| 描述符中的**类型参数变量**（未实化） | 裸名（`T`、`E`…） | 不加 `R...;` 包装 |

#### 示例

| 示例                                      | 含义                            |
|:----------------------------------------|:-------------------------------|
| `Rling/std/Any;`                        | `ling.std.Any`                 |
| `Rling/std/Array<I>;`                   | `ling.std.Array<Int>`          |
| `Rling/std/Array<Rling/std/Any;>;`      | `ling.std.Array<Any>`          |
| `Rling/std/Array<T>;`                   | `ling.std.Array<T>`（T 为类型参数） |
| `Rsqrt/second/平方喵;`                    | `sqrt.second.平方喵`              |
| `R->;`                                  | 函数类型占位                        |

根类 `lspled.lang.Any`（在 LingSpled 标准库中为 `ling.std.Any`）是所有类的最终父类；  
其在字节码父类字段中以哨兵字符串 `"Any"` 表示（不使用 FQN），虚方法查找遇到 `"Any"` 时终止。

### 函数 FQN

```
<rootPackage>.<subPackage>#functionName<TypeParams>(<paramDescs>)<returnDesc>
```

- 包路径各段之间用 `.` 分隔（与类 FQN 的 `/` 不同）
- `#` 分隔包路径与函数名
- `<TypeParams>` 为泛型参数约束列表（无泛型时为空 `<>` 或省略）
- 括号内为参数描述符序列，括号后为返回类型描述符

| 示例                                              | 含义                                   |
|:------------------------------------------------|:-------------------------------------|
| `ling.std#println<>(Rling/std/Any;)V`           | `ling.std.println(Any): Unit`        |
| `ling.std#println<>(IRling/std/Array<I>;)V`     | `ling.std.println(Int, Array<Int>): Unit` |

### 常量池 String 条目示例

1. `"Hello, World!"` (Latin-1)：

```
0x06  0x00      0x0D 0x00   0x48 0x65 ... 0x21
Tag   Encoding  Length(LE)  Contents
```

2. `"我喜欢你 awa 😁"` (UTF-16)：

```
0x06  0x01      0x0B 0x00   0x11 0x62 0x9C 0x55 ... 0x3D 0xD8 0x01 0xDE
Tag   Encoding  Length(LE)  Contents (UTF-16LE)
```

## 属性条目

每个属性条目的公共头：

```
2B  属性标识 (tag)
nB  属性具体内容 (据 tag 而定)
```

属性 tag 分配：

|  Tag   |  类型  |
|:------:|:----:|
| 0x0000 | 顶层变量 |
| 0x0001 |  函数  |
| 0x0002 |  类   |

---

### 0x0000 — 顶层变量

```
2B  nameIdx        CP 索引 → 变量名 CString
2B  typeIdx        CP 索引 → 类型名 CString（或 Class 条目）
2B  accessFlags    访问修饰符位掩码（见下表）
4B  initCodeLen    初始化字节码长度（0 表示无初始化器）
nB  initCode       初始化字节码（执行后栈顶值即为初始值）
```

---

### 0x0001 — 函数

```
2B  nameIdx        CP 索引 → 函数名 CString
2B  descriptorIdx  CP 索引 → FunctionDescriptor 条目
2B  accessFlags    访问修饰符位掩码
2B  paramSlots     参数占用的 slot 数（宽类型占 2）
2B  localSlots     函数体内局部变量额外占用的 slot 数
4B  codeLen        字节码长度（字节数）
nB  code           字节码
```

总 slot 数 = `paramSlots + localSlots`。方法的 `this` 计入 `paramSlots` 第 0 slot。

---

### 0x0002 — 类

```
2B  nameIdx        CP 索引 → 类名 CString
2B  accessFlags    访问修饰符位掩码
2B  superIdx       CP 索引 → 父类 Class 条目（所有类均有父类，最终为 lspled.lang.Any）
2B  fieldCount     字段数量
[fieldCount 个字段条目]
2B  methodCount    方法数量
[methodCount 个方法条目，格式同函数属性（不含 tag）]
```

#### 字段条目

```
2B  nameIdx        CP 索引 → 字段名 CString
2B  typeIdx        CP 索引 → 类型名 CString（或 Class 条目）
2B  accessFlags    访问修饰符位掩码
```

#### 保留方法名

| 名称                  | 用途                       |
|:-------------------:|:------------------------:|
| `<constructor>`     | 实例构造函数（`new` 指令调用）       |
| `<classinitializer>` | 静态初始化块（`static { … }`）  |

---

### accessFlags 位掩码

|  位   |    含义     |
|:----:|:---------:|
| 0x01 |  public   |
| 0x02 |  private  |
| 0x04 | protected |
| 0x08 |  static   |
| 0x10 |   final   |
| 0x20 | override  |

# 字节码

所有指令均为 **1 字节操作码**, 可变长操作数紧随其后, 字节序同文件头 (小端)。操作码数值见 `Bytecodes.kt` 中的 `const val`
常量。

## 分组概览

|    操作码范围    |        组        |
|:-----------:|:---------------:|
|   `0x00`    |   杂项 (`nop`)    |
| `0x01–0x0F` |       栈操作       |
| `0x10–0x27` |       算术        |
| `0x28–0x38` |    位 / 逻辑运算     |
| `0x39–0x4A` |       比较        |
| `0x4B–0x5A` |  对象 / 函数 / 同步   |
| `0x5B–0x6B` |       控制流       |
| `0x6C–0x71` |      局部变量       |
| `0x72–0x7B` |       数组        |
| `0x7C–0x8A` | 常量池 / 类型转换 / 调试 |

## 操作数格式 (固定长度)

大多数指令无操作数；有操作数的指令格式如下：

- `push8` — 1 字节立即数
- `push16` — 2 字节有符号立即数
- `push32` — 4 字节立即数
- `push64` — 8 字节立即数
- `load` / `store` / `wload` / `wstore` / `aload` / `astore` — 2 字节 slot 索引
- `ldc` / `ldc2` — 2 字节常量池索引
- `ldfield` / `stfield` — 2 字节常量池索引 (字段名 Rclassname)
- `invokespec` / `invokesupr` / `invokeinst` / `invokestat` — 2 字节常量池索引 (FunctionDescriptor，含名称与描述符)
- `call` — 2 字节函数表索引
- `new` — 2 字节函数表索引 (指向 `<constructor>` 条目)
- `cast` / `isinstance` — 2 字节常量池索引 (类名 Rclassname)
- `jump` / `jeq` / `jne` / `jlt` / `jle` / `jgt` / `jge` / `jnul` / `jnnul` — 2 字节有符号相对偏移 (Relative-16)；目标地址 = **读完本指令后的 PC**（即指令首字节 + 3）± disp
- `wjump` — 4 字节有符号相对偏移 (Relative-32); 目标地址 = **读完本指令后的 PC**（即指令首字节 + 5）± offset
- `linenum` — 2 字节源码行号, 无栈效应

## 关键指令语义

### 函数调用

|      指令      |   操作数    |        弹出         | 推入  | 说明                        |
|:------------:|:--------:|:-----------------:|:---:|:--------------------------|
|    `call`    |  函数表索引   |      args...      | 返回值 | 调用当前文件中的顶层函数              |
| `invokeinst` | FD CP 索引 |   obj, args...    | 返回值 | 实例方法，按 name+descriptor 分派 |
| `invokestat` | FD CP 索引 | classRef, args... | 返回值 | 静态方法，弹出栈顶 class 引用后分派     |
| `invokespec` | FD CP 索引 |   obj, args...    | 返回值 | 非虚分派（private / super 构造前） |
| `invokesupr` | FD CP 索引 |   obj, args...    | 返回值 | 父类方法分派                    |

### 对象创建

`new CTOR_INDEX`

- 操作数：函数表中 `<constructor>` 的索引
- 弹出：栈顶 class 引用，再弹出所有构造参数
- 推入：新分配的对象引用

### 同步

|          指令序列           | 用途                                                      |
|:-----------------------:|:--------------------------------------------------------|
| `syncbegin` … `syncend` | 整个函数体加锁（synchronized function）                          |
|  `syncref` … `syncend`  | 对特定对象加锁（synchronized (obj) { … }），`syncref` 弹出栈顶引用作为监视器 |

# 操作数栈

小端序, 使用 32 位槽位 (`Slot`) 组织.

```
| Slot 0 | Slot 1 | Slot 2 | Slot 3 | Slot 4 | Slot 5 | Slot 6 | Slot 7 |
|   10   |   32   |   8    |   2147483649    |  1145  |  2147  |  721   |
```

当宽值 (Long / Double) 在槽位中, 按照如下规则进行存储:

```
原数: 0x1234_5678_9ABC_DEF0
| Slot 0     | Slot 1     |
| 0x9ABCDEF0 | 0x12345678 |
```
