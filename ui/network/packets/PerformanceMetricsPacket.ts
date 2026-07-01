import { Packet } from '../Packet';
import { PacketBuffer } from '../PacketBuffer';

/** Live performance metrics pushed from the client for the performance-page gauge. */
export class PerformanceMetricsPacket implements Packet {
  packetId = 25;
  fps = 0;
  lowFps = 0;
  ping = 0;

  write(buffer: PacketBuffer): void {
    buffer.writeInt(this.fps);
    buffer.writeInt(this.lowFps);
    buffer.writeInt(this.ping);
  }

  read(buffer: PacketBuffer): void {
    this.fps = buffer.readInt();
    this.lowFps = buffer.readInt();
    this.ping = buffer.readInt();
  }
}
