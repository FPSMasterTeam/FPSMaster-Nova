import { Packet } from './Packet';
import { GuiLoadAckPacket, GuiLoadEventPacket } from './packets/GuiLoadPackets';

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
    // Register other packets here
  }
}

PacketRegistry.initialize();
