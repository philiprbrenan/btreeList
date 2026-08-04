# Serial Input Register Configuration Processor (SIRCP)

## 1. Overview

The **Serial Input Register Configuration Processor (SIRCP)** is a compact command-driven processor designed to
initialize and modify large hardware configuration registers through a simple three-wire serial interface using a
minimal command stream.

Unlike a conventional processor, SIRCP is optimized for configuration management rather than computation. Its
instruction set minimizes the number of serial bytes required to initialize, update, copy, and manipulate large
configuration register banks.

Typical applications include:

- ASIC configuration loading
- FPGA configuration loading
- Hardware control register programming
- Sparse initialisation of large register banks
- Compression of repetitive configuration data
- Remote device initialisation over low-pin-count interfaces

The processor contains sixteen large general-purpose registers, a transfer register, and control registers used to manage
serial data movement.

---

# 2. Serial Interface

## 2.1 Signals

| Signal | Direction | Description |
|---|---|---|
| `clk` | Input | Serial communication clock |
| `start` | Input | Indicates the beginning of a new command byte |
| `data` | Input | Serial data input |

## 2.2 Timing

Data is sampled on the rising edge of `clk`.

Instruction bytes are transmitted **least significant bit first**.

After the eighth bit has been received, the instruction is decoded and executed immediately. There is no instruction
pipeline.

---

# 3. Internal Architecture

The processor contains:

- Sixteen general-purpose configuration registers GPR0 - GPR15
- One transfer register  TFR
- Current Register Pointer (CRP)
- Current Position Register (CPR)
- Load Count Register (LCR)
- Copy Count Register (CCR)

The register file is treated as a continuous byte-addressable memory space, allowing operations to cross register
boundaries.

## 3.1 Current Register Pointer (CRP)

The **Current Register Pointer** is a four-bit register selecting the active general-purpose register.

```
0 <= CRP <= 15
```

## 3.2 Current Position Register (CPR)

The **Current Position Register** is a 32-bit byte pointer identifying the current byte position within the selected
register space.

The CPR counts bytes rather than bits.

```
0 <= CPR < (16 * REG_WIDTH / 8)
```

The CPR is automatically incremented after byte operations.

## 3.3 Load Count Register (LCR)

The **Load Count Register**  is a 32-bit register that specifies the number of bytes that are copied directly from the serial input stream into
the register space.

For each byte:

1. Receive one byte from the serial interface.
2. Write the byte at the current position.
3. Increment CPR.
4. Decrement LCR.

The operation continues until LCR reaches zero.

## 3.4 Copy Count Register (CCR)

The **Copy Count Register**  is a 32-bit register that specifies the number of bytes involved in copy and fill operations.

CCR-controlled operations transfer bytes between the transfer register and the general-purpose register space, beginning at the current position.

## 3.5 General Register File

The processor contains sixteen general-purpose registers:

```
R0 ... R15
```

Each register has width:

```
REG_WIDTH bits
```

The registers are logically chained together as a continuous byte-addressable space.

This permits loading, copying, and filling operations to continue across register boundaries.

## 3.6 Transfer Register TFR

The transfer register has the same width as a general-purpose register.

It provides temporary storage for copy operations and byte-level manipulation instructions.

---

# 4. Instruction Format

Each instruction occupies one byte:

```
bit 7          bit 4 bit 3          bit 0
+----------------+----------------+
|    OPCODE      |    OPERAND     |
+----------------+----------------+
```

The upper nibble specifies the instruction. The lower nibble provides an operand.

---

# 5. Primary Instruction Set

| Opcode | Mnemonic | Description |
|---|---|---|
| 0 | SETCRP | Select current register |
| 1 | SETCPR | Set current byte position |
| 2 | SETLCR | Set load count register |
| 3 | SETCCR | Set Copy Count Register |
| 4 | DUP | Duplicate previously loaded bytes after the current position |
| 5 | COPYFROM | Copy selected register into the Transfer Register |
| 6 | COPYTO | Copy the Transfer Register into selected register and make it current |
| 7 | BYTEFROM | Copy one byte from selected register into the Transfer Register |
| 8 | BYTETO | Copy one byte from the Transfer Register into selected register |
| 9 | BYTESFROM | Copy CCR bytes from register space into the Transfer Register |
| A | BYTESTO | Copy CCR bytes from the Transfer Register into register space |
| B | EXECREG | Execute selected register contents as an instruction stream |
| C | LOADCPR | Load current byte position register from the lowest bits of the general purpose register indicted in the operand|
| D | LOADLCR | Load load count            register from the lowest bits of the general purpose register indicted in the operand|
| E | LOADCCR | Load Copy Count            Register from the lowest bits of the general purpose register indicted in the operand|
| F | EXT | Extended instruction |

---

# 6. Extended Instructions

Opcode:

```
1111
```

The operand selects one of sixteen extended operations.

| Operand | Mnemonic | Description |
|---|---|---|
| 0 | NOP | No operation |
| 1 | CLEAR | Clear control registers and transfer register |
| 2 | RESET | Clear all control registers and general-purpose registers |
| 3 | CRC | Update or verify configuration CRC |
| 4 | BYTEALL | Broadcast current byte to all general-purpose registers |
| 5 | COPYALL | Copy Transfer Register contents to all general-purpose registers |
| 6 | EXEC | Transfer control to hardware using configured registers |
| 7 | VERSION | Protocol version identification |
| 8 | TEST | Manufacturing test operation |
| 9 | CLEAR_REG | Clear selected register |
| A | SET_REG | Set all bits in selected register |
| B | CLEAR_BYTE | Clear byte at current position |
| C | SET_BYTE | Set byte at current position |
| D | FILL | Fill CCR bytes using the next serial byte as the fill value |
| E | FILL0 | Fill CCR bytes with zeros from the current position in the current register |
| F | FILL1 | Fill CCR bytes with ones from the current position in the current register |

---

# 7. FILL Instruction

The FILL instruction efficiently generates repeated configuration data.

Operation:

1. Receive one byte from the serial interface.
2. Use the byte as the fill value.
3. Write the value for CCR bytes starting at the current position.
4. Increment the current position after each byte.

The operation automatically crosses register boundaries.

---

# 8. Synthesis Requirements

The implementation shall:

- Be fully synthesizable using standard Verilog
- Contain no delays
- Contain no behavioural timing constructs
- Use synchronous logic only
- Support arbitrary `REG_WIDTH` values
- Synthesise cleanly using Yosys and conventional ASIC synthesis tools

---
