import { Packet } from './Packet';
import { PacketBuffer } from './PacketBuffer';

export class PacketSerializer {
  
  static serialize(packet: Packet): string {
    const buffer = new PacketBuffer();
    packet.write(buffer);
    const base64 = buffer.toBase64();
    
    return JSON.stringify({
      packetId: packet.packetId,
      data: base64
    });
  }

  static deserialize(jsonString: string, factory: (id: number) => Packet | null): Packet | null {
    try {
      const obj = JSON.parse(jsonString);
      if (typeof obj.packetId !== 'number') return null;
      
      const packet = factory(obj.packetId);
      if (!packet) return null;

      if (obj.data) {
        const buffer = PacketBuffer.fromBase64(obj.data);
        packet.read(buffer);
      }
      
      return packet;
    } catch (e) {
      console.error('Failed to deserialize packet', e);
      return null;
    }
  }
}
