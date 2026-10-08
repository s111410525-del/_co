// This file is part of www.nand2tetris.org
// and the book "The Elements of Computing Systems"
// by Nisan and Schocken, MIT Press.
// File name: projects/04/Mult.asm

// Multiplies R0 and R1 and stores the result in R2.
// (R0, R1, R2 refer to RAM[0], RAM[1], and RAM[2], respectively.)

// 初始化 R2 = 0
    @R2
    M=0

// 將 R1 的值複製給計數器 i
    @R1
    D=M
    @i
    M=D

(LOOP)
    // 若 i <= 0 則跳轉到 END
    @i
    D=M
    @END
    D;JLE

    // R2 = R2 + R0
    @R0
    D=M
    @R2
    M=M+D

    // i = i - 1
    @i
    M=M-1

    // 跳回 LOOP 繼續迴圈
    @LOOP
    0;JMP

(END)
    // 無限迴圈結束程式
    @END
    0;JMP