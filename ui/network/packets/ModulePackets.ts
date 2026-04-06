import { Packet } from '../Packet';
import { PacketBuffer } from '../PacketBuffer';

export interface RemoteModuleEntry {
  id: string;
  category: string;
  enabled: boolean;
}

export class ModuleListRequestPacket implements Packet {
  packetId = 11;

  write(_buffer: PacketBuffer): void {
    // No payload
  }

  read(_buffer: PacketBuffer): void {
    // No payload
  }
}

export class ModuleListPacket implements Packet {
  packetId = 12;
  modules: RemoteModuleEntry[] = [];

  write(buffer: PacketBuffer): void {
    buffer.writeInt(this.modules.length);
    for (const module of this.modules) {
      buffer.writeString(module.id);
      buffer.writeString(module.category);
      buffer.writeBoolean(module.enabled);
    }
  }

  read(buffer: PacketBuffer): void {
    const moduleCount = buffer.readInt();
    this.modules = [];

    for (let i = 0; i < moduleCount; i++) {
      this.modules.push({
        id: buffer.readString() || '',
        category: buffer.readString() || '',
        enabled: buffer.readBoolean(),
      });
    }
  }
}

export class ModuleTogglePacket implements Packet {
  packetId = 13;
  moduleId: string = '';
  enabled: boolean = false;

  constructor(moduleId: string = '', enabled: boolean = false) {
    this.moduleId = moduleId;
    this.enabled = enabled;
  }

  write(buffer: PacketBuffer): void {
    buffer.writeString(this.moduleId);
    buffer.writeBoolean(this.enabled);
  }

  read(buffer: PacketBuffer): void {
    this.moduleId = buffer.readString() || '';
    this.enabled = buffer.readBoolean();
  }
}
