import { Packet } from './Packet';
import { AccountLoginPacket, AccountLogoutPacket, AccountStatusPacket, AccountStatusRequestPacket } from './packets/AccountPackets';
import { ClientConfigPacket, ClientConfigRequestPacket, ClientConfigUpdatePacket } from './packets/ClientConfigPackets';
import { ConfigProfileActionPacket, ConfigProfilesPacket, ConfigProfilesRequestPacket } from './packets/ConfigProfilePackets';
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
    this.register(15, () => new ClientConfigRequestPacket());
    this.register(16, () => new ClientConfigPacket());
    this.register(17, () => new ClientConfigUpdatePacket());
    this.register(18, () => new AccountStatusRequestPacket());
    this.register(19, () => new AccountStatusPacket());
    this.register(20, () => new AccountLoginPacket());
    this.register(21, () => new AccountLogoutPacket());
    this.register(22, () => new ConfigProfilesRequestPacket());
    this.register(23, () => new ConfigProfilesPacket());
    this.register(24, () => new ConfigProfileActionPacket());
  }
}

PacketRegistry.initialize();
