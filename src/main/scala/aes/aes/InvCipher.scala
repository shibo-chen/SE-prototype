package aes

import chisel3._
import chisel3.util._

class InvCipherIO extends Bundle{
  val ciphertext = Input(Vec(16, UInt(8.W)))
  val roundKey = Input(Vec(16, UInt(8.W)))
  val start = Input(Bool())
  val state_out = Output(Vec(16, UInt(8.W)))
  val state_out_valid = Output(Bool())
}

// implements AES_Decrypt
class InvCipher extends Module {
  val KeyLength: Int = 16
  val Nr: Int = 10 // 10, 12, 14 rounds
  val Nrplus1: Int = Nr + 1 // 10+1, 12+1, 14+1

  val io = IO(new InvCipherIO)

  // Instantiate module objects
  val AddRoundKeyModule = AddRoundKey()
  val InvSubBytesModule = InvSubBytes()
  val InvShiftRowsModule = InvShiftRows()
  val InvMixColumnsModule = InvMixColumns()

  // Internal variables
  val initValues = Seq.fill(16)(0.U(8.W))
  val state = RegInit(VecInit(initValues))
  val rounds = RegInit(0.U(4.W))

  // STM
  val sIdle :: sInitialAR :: sBusy :: Nil = Enum(3)
  val STM = RegInit(sIdle)

  switch(STM) {
    is(sIdle) {
      when(io.start) {
        STM := sInitialAR
      } // Start cipher
      rounds := 0.U
    }
    is(sInitialAR) {
      rounds := rounds + 1.U
      STM := sBusy
    }
    is(sBusy) {
      rounds := rounds + 1.U
      when(rounds === Nr.U) {
        STM := sIdle
      }
    }
  }

  // InvShiftRows state
  InvShiftRowsModule.io.state_in := state

  // InvSubBytes state
  InvSubBytesModule.io.state_in := InvShiftRowsModule.io.state_out

  // AddRoundKey state
  AddRoundKeyModule.io.state_in := Mux(STM === sInitialAR, io.ciphertext, InvSubBytesModule.io.state_out)
  AddRoundKeyModule.io.roundKey := io.roundKey

  // InvMixColumns state
  InvMixColumnsModule.io.state_in := AddRoundKeyModule.io.state_out

  state := Mux(STM =/= sIdle, Mux((rounds > 0.U) & (rounds < Nr.U), InvMixColumnsModule.io.state_out, AddRoundKeyModule.io.state_out), VecInit(initValues))

  // Set state_out_valid true when cipher ends
  io.state_out_valid := rounds === Nrplus1.U
  io.state_out := Mux(rounds === Nrplus1.U, state, RegInit(VecInit(initValues)))

  // Debug statements
  //  printf("D_STM: %d, rounds: %d, valid: %d\n", STM, rounds, io.state_out_valid)
  //  printf("D_roundKey: %x %x %x %x %x %x %x %x %x %x %x %x %x %x %x %x\n", io.roundKey(0), io.roundKey(1), io.roundKey(2), io.roundKey(3), io.roundKey(4), io.roundKey(5), io.roundKey(6), io.roundKey(7), io.roundKey(8), io.roundKey(9), io.roundKey(10), io.roundKey(11), io.roundKey(12), io.roundKey(13), io.roundKey(14), io.roundKey(15))
  //  printf("state: %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d\n", state(0), state(1), state(2), state(3), state(4), state(5), state(6), state(7), state(8), state(9), state(10), state(11), state(12), state(13), state(14), state(15))
}

object InvCipher {
  def apply(): InvCipher = Module(new InvCipher)
}