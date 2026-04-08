import { Packet } from '../Packet';
import { PacketBuffer } from '../PacketBuffer';

export enum RemoteModuleValueType {
  BOOLEAN = 0,
  NUMBER = 1,
  STRING = 2,
}

export interface RemoteModuleValueEntry {
  id: string;
  type: RemoteModuleValueType;
  booleanValue: boolean;
  numberValue: number;
  stringValue: string | null;
  min: number;
  max: number;
  step: number;
  unit: string;
}

export interface RemoteModuleEntry {
  id: string;
  category: string;
  enabled: boolean;
  values: RemoteModuleValueEntry[];
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
      buffer.writeInt(module.values.length);

      for (const value of module.values) {
        buffer.writeString(value.id);
        buffer.writeInt(value.type);
        buffer.writeBoolean(value.booleanValue);
        buffer.writeDouble(value.numberValue);
        buffer.writeString(value.stringValue);
        buffer.writeDouble(value.min);
        buffer.writeDouble(value.max);
        buffer.writeDouble(value.step);
        buffer.writeString(value.unit);
      }
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
        values: Array.from({ length: buffer.readInt() }, () => ({
          id: buffer.readString() || '',
          type: buffer.readInt() as RemoteModuleValueType,
          booleanValue: buffer.readBoolean(),
          numberValue: buffer.readDouble(),
          stringValue: buffer.readString(),
          min: buffer.readDouble(),
          max: buffer.readDouble(),
          step: buffer.readDouble(),
          unit: buffer.readString() || '',
        })),
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

export class ModuleValueUpdatePacket implements Packet {
  packetId = 14;
  moduleId: string = '';
  valueId: string = '';
  type: RemoteModuleValueType = RemoteModuleValueType.BOOLEAN;
  booleanValue: boolean = false;
  numberValue: number = 0;
  stringValue: string | null = null;

  constructor(
    moduleId: string = '',
    valueId: string = '',
    type: RemoteModuleValueType = RemoteModuleValueType.BOOLEAN,
    value: boolean | number | string = false,
  ) {
    this.moduleId = moduleId;
    this.valueId = valueId;
    this.type = type;

    if (type === RemoteModuleValueType.BOOLEAN) {
      this.booleanValue = Boolean(value);
    } else if (type === RemoteModuleValueType.NUMBER) {
      this.numberValue = Number(value);
    } else {
      this.stringValue = String(value);
    }
  }

  write(buffer: PacketBuffer): void {
    buffer.writeString(this.moduleId);
    buffer.writeString(this.valueId);
    buffer.writeInt(this.type);
    buffer.writeBoolean(this.booleanValue);
    buffer.writeDouble(this.numberValue);
    buffer.writeString(this.stringValue);
  }

  read(buffer: PacketBuffer): void {
    this.moduleId = buffer.readString() || '';
    this.valueId = buffer.readString() || '';
    this.type = buffer.readInt() as RemoteModuleValueType;
    this.booleanValue = buffer.readBoolean();
    this.numberValue = buffer.readDouble();
    this.stringValue = buffer.readString();
  }
}
