import { Packet } from '../Packet';
import { PacketBuffer } from '../PacketBuffer';

export class ConfigProfilesRequestPacket implements Packet {
  packetId = 22;

  write(_buffer: PacketBuffer): void {
    // No payload
  }

  read(_buffer: PacketBuffer): void {
    // No payload
  }
}

export class ConfigProfilesPacket implements Packet {
  packetId = 23;
  success = true;
  message = '';
  activeProfile = '';
  profiles: string[] = [];

  write(buffer: PacketBuffer): void {
    buffer.writeBoolean(this.success);
    buffer.writeString(this.message);
    buffer.writeString(this.activeProfile);
    buffer.writeInt(this.profiles.length);
    for (const profile of this.profiles) {
      buffer.writeString(profile);
    }
  }

  read(buffer: PacketBuffer): void {
    this.success = buffer.readBoolean();
    this.message = buffer.readString() || '';
    this.activeProfile = buffer.readString() || '';
    const count = buffer.readInt();
    this.profiles = Array.from({ length: count }, () => buffer.readString() || '');
  }
}

export class ConfigProfileActionPacket implements Packet {
  packetId = 24;

  constructor(
    public action: string = '',
    public name: string = '',
    public targetName: string = '',
  ) {}

  write(buffer: PacketBuffer): void {
    buffer.writeString(this.action);
    buffer.writeString(this.name);
    buffer.writeString(this.targetName);
  }

  read(buffer: PacketBuffer): void {
    this.action = buffer.readString() || '';
    this.name = buffer.readString() || '';
    this.targetName = buffer.readString() || '';
  }
}
