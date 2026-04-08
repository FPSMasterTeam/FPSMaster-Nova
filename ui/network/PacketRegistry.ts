import { Packet } from './Packet';
import { GuiLoadAckPacket, GuiLoadEventPacket } from './packets/GuiLoadPackets';
import { ModuleListPacket, ModuleListRequestPacket, ModuleTogglePacket, ModuleValueUpdatePacket } from './packets/ModulePackets';

export class PacketRegistry {
  private static packetMap: Map<number, () => Packet> = new Map();

  static register(id: number, factory: () => Packet) {
    this.packetMap.set(id, factory);
  }

  static create(id: number): Packet | null {
    const factory = this.packetMap.get(id);
    return factory ? factory() : null;
  }

  static initialize() {
    this.register(9, () => new GuiLoadEventPacket());
    this.register(10, () => new GuiLoadAckPacket());
    this.register(11, () => new ModuleListRequestPacket());
    this.register(12, () => new ModuleListPacket());
    this.register(13, () => new ModuleTogglePacket());
    this.register(14, () => new ModuleValueUpdatePacket());
  }
}

PacketRegistry.initialize();
