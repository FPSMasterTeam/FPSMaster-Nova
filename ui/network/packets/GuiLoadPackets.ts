import { Packet } from '../Packet';
import { PacketBuffer } from '../PacketBuffer';

export class GuiLoadEventPacket implements Packet {
  packetId = 9;
  eventType: string = "open";
  timestamp: number = 0;
  extraData: string | null = null;

  write(buffer: PacketBuffer): void {
    buffer.writeString(this.eventType);
    buffer.writeLong(this.timestamp);
    buffer.writeString(this.extraData);
  }

  read(buffer: PacketBuffer): void {
    this.eventType = buffer.readString() || "open";
    this.timestamp = buffer.readLong();
    this.extraData = buffer.readString();
  }
}

export class GuiLoadAckPacket implements Packet {
  packetId = 10;
  success: boolean = false;
  message: string = "";
  timestamp: number = 0;

  constructor(success: boolean = false, message: string = "", timestamp: number = Date.now()) {
    this.success = success;
    this.message = message;
    this.timestamp = timestamp;
  }

  write(buffer: PacketBuffer): void {
    buffer.writeBoolean(this.success);
    buffer.writeString(this.message);
    buffer.writeLong(this.timestamp);
  }

  read(buffer: PacketBuffer): void {
    this.success = buffer.readBoolean();
    this.message = buffer.readString() || "";
    this.timestamp = buffer.readLong();
  }
}
