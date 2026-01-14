/**
 * PacketBuffer.ts
 * 
 * Helper class to read and write binary data (Big Endian)
 * Compatible with Java DataOutputStream / DataInputStream
 */
export class PacketBuffer {
  private buffer: Uint8Array;
  private view: DataView;
  private offset: number = 0;

  constructor(sizeOrBuffer: number | Uint8Array | ArrayBuffer = 1024) {
    if (typeof sizeOrBuffer === 'number') {
      this.buffer = new Uint8Array(sizeOrBuffer);
      this.view = new DataView(this.buffer.buffer);
    } else if (sizeOrBuffer instanceof Uint8Array) {
      this.buffer = sizeOrBuffer;
      this.view = new DataView(this.buffer.buffer, this.buffer.byteOffset, this.buffer.byteLength);
    } else {
      this.buffer = new Uint8Array(sizeOrBuffer);
      this.view = new DataView(this.buffer.buffer);
    }
  }

  private ensureCapacity(needed: number) {
    if (this.offset + needed > this.buffer.length) {
      const newSize = Math.max(this.buffer.length * 2, this.offset + needed);
      const newBuffer = new Uint8Array(newSize);
      newBuffer.set(this.buffer);
      this.buffer = newBuffer;
      this.view = new DataView(this.buffer.buffer);
    }
  }

  // --- Write Methods ---

  writeBoolean(value: boolean) {
    this.writeByte(value ? 1 : 0);
  }

  writeByte(value: number) {
    this.ensureCapacity(1);
    this.view.setInt8(this.offset, value);
    this.offset += 1;
  }

  writeInt(value: number) {
    this.ensureCapacity(4);
    this.view.setInt32(this.offset, value, false); // false for Big Endian
    this.offset += 4;
  }

  writeLong(value: number) {
    this.ensureCapacity(8);
    // JavaScript numbers are doubles (53 bit integer precision).
    // For timestamps or reasonable longs, we can use BigInt or split it.
    // Here we use BigInt for correctness.
    this.view.setBigInt64(this.offset, BigInt(value), false);
    this.offset += 8;
  }

  writeFloat(value: number) {
    this.ensureCapacity(4);
    this.view.setFloat32(this.offset, value, false);
    this.offset += 4;
  }

  writeDouble(value: number) {
    this.ensureCapacity(8);
    this.view.setFloat64(this.offset, value, false);
    this.offset += 8;
  }

  writeString(value: string | null) {
    if (value === null) {
      this.writeInt(-1);
      return;
    }
    const encoder = new TextEncoder();
    const bytes = encoder.encode(value);
    this.writeInt(bytes.length);
    this.ensureCapacity(bytes.length);
    this.buffer.set(bytes, this.offset);
    this.offset += bytes.length;
  }

  // --- Read Methods ---

  readBoolean(): boolean {
    return this.readByte() !== 0;
  }

  readByte(): number {
    const val = this.view.getInt8(this.offset);
    this.offset += 1;
    return val;
  }

  readInt(): number {
    const val = this.view.getInt32(this.offset, false);
    this.offset += 4;
    return val;
  }

  readLong(): number {
    const val = this.view.getBigInt64(this.offset, false);
    this.offset += 8;
    // Convert back to number (beware of precision loss for very large numbers > 2^53)
    // Timestamps are safe.
    return Number(val);
  }

  readFloat(): number {
    const val = this.view.getFloat32(this.offset, false);
    this.offset += 4;
    return val;
  }

  readDouble(): number {
    const val = this.view.getFloat64(this.offset, false);
    this.offset += 8;
    return val;
  }

  readString(): string | null {
    const len = this.readInt();
    if (len === -1) return null;
    if (len < 0) throw new Error(`Invalid string length: ${len}`);
    
    const bytes = this.buffer.subarray(this.offset, this.offset + len);
    this.offset += len;
    const decoder = new TextDecoder();
    return decoder.decode(bytes);
  }

  // --- Utility ---

  toByteArray(): Uint8Array {
    return this.buffer.subarray(0, this.offset);
  }

  static fromBase64(base64: string): PacketBuffer {
    const binaryString = atob(base64);
    const len = binaryString.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    return new PacketBuffer(bytes);
  }

  toBase64(): string {
    const bytes = this.toByteArray();
    let binary = '';
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }
}
