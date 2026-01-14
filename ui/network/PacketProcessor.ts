import { Packet } from './Packet';

export type PacketHandler<T extends Packet> = (packet: T) => void;

export class PacketProcessor {
  private static handlers: Map<number, PacketHandler<any>[]> = new Map();

  static register<T extends Packet>(packetId: number, handler: PacketHandler<T>) {
    if (!this.handlers.has(packetId)) {
      this.handlers.set(packetId, []);
    }
    this.handlers.get(packetId)!.push(handler);
  }

  static unregister<T extends Packet>(packetId: number, handler: PacketHandler<T>) {
      const list = this.handlers.get(packetId);
      if (list) {
          const index = list.indexOf(handler);
          if (index !== -1) {
              list.splice(index, 1);
          }
      }
  }

  static process(packet: Packet) {
    const handlers = this.handlers.get(packet.packetId);
    if (handlers) {
      handlers.forEach(handler => {
        try {
          handler(packet);
        } catch (e) {
          console.error(`Error handling packet ${packet.packetId}`, e);
        }
      });
    }
  }
}
