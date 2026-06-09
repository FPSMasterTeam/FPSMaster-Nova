import { Packet } from '../Packet';
import { PacketBuffer } from '../PacketBuffer';

export class ClientConfigRequestPacket implements Packet {
  packetId = 15;

  write(_buffer: PacketBuffer): void {
    // No payload
  }

  read(_buffer: PacketBuffer): void {
    // No payload
  }
}

export class ClientConfigPacket implements Packet {
  packetId = 16;
  musicVolume = 75;
  anonymousDataEnabled = false;
  telemetryInstanceId = '';
  background = 'panorama_1';
  oobeCompleted = false;
  antiCheatEnabled = true;
  classicBackgroundColor = 0xff000000;
  classicBackgroundHue = 0;
  classicBackgroundSaturation = 0;
  classicBackgroundBrightness = 0;
  classicBackgroundAlpha = 1;
  classicBackgroundMode = 'STATIC';

  write(buffer: PacketBuffer): void {
    buffer.writeDouble(this.musicVolume);
    buffer.writeBoolean(this.anonymousDataEnabled);
    buffer.writeString(this.telemetryInstanceId);
    buffer.writeString(this.background);
    buffer.writeBoolean(this.oobeCompleted);
    buffer.writeBoolean(this.antiCheatEnabled);
    buffer.writeInt(this.classicBackgroundColor);
    buffer.writeFloat(this.classicBackgroundHue);
    buffer.writeFloat(this.classicBackgroundSaturation);
    buffer.writeFloat(this.classicBackgroundBrightness);
    buffer.writeFloat(this.classicBackgroundAlpha);
    buffer.writeString(this.classicBackgroundMode);
  }

  read(buffer: PacketBuffer): void {
    this.musicVolume = buffer.readDouble();
    this.anonymousDataEnabled = buffer.readBoolean();
    this.telemetryInstanceId = buffer.readString() ?? '';
    this.background = buffer.readString() ?? 'panorama_1';
    this.oobeCompleted = buffer.readBoolean();
    this.antiCheatEnabled = buffer.readBoolean();
    this.classicBackgroundColor = buffer.readInt();
    this.classicBackgroundHue = buffer.readFloat();
    this.classicBackgroundSaturation = buffer.readFloat();
    this.classicBackgroundBrightness = buffer.readFloat();
    this.classicBackgroundAlpha = buffer.readFloat();
    this.classicBackgroundMode = buffer.readString() ?? 'STATIC';
  }
}

export class ClientConfigUpdatePacket implements Packet {
  packetId = 17;
  updateMusicVolume = true;
  musicVolume = 75;
  updateAnonymousDataEnabled = false;
  anonymousDataEnabled = false;
  updateClientPreferences = false;
  background = 'panorama_1';
  oobeCompleted = false;
  antiCheatEnabled = true;
  classicBackgroundColor = 0xff000000;
  classicBackgroundHue = 0;
  classicBackgroundSaturation = 0;
  classicBackgroundBrightness = 0;
  classicBackgroundAlpha = 1;
  classicBackgroundMode = 'STATIC';

  constructor(
    musicVolume: number = 75,
    options: {
      updateMusicVolume?: boolean;
      anonymousDataEnabled?: boolean;
      clientPreferences?: Partial<ClientConfigPacket>;
    } = {},
  ) {
    this.updateMusicVolume = options.updateMusicVolume ?? true;
    this.musicVolume = musicVolume;
    if (options.anonymousDataEnabled !== undefined) {
      this.updateAnonymousDataEnabled = true;
      this.anonymousDataEnabled = options.anonymousDataEnabled;
    }
    if (options.clientPreferences) {
      this.updateClientPreferences = true;
      Object.assign(this, options.clientPreferences);
    }
  }

  write(buffer: PacketBuffer): void {
    buffer.writeBoolean(this.updateMusicVolume);
    buffer.writeDouble(this.musicVolume);
    buffer.writeBoolean(this.updateAnonymousDataEnabled);
    buffer.writeBoolean(this.anonymousDataEnabled);
    buffer.writeBoolean(this.updateClientPreferences);
    buffer.writeString(this.background);
    buffer.writeBoolean(this.oobeCompleted);
    buffer.writeBoolean(this.antiCheatEnabled);
    buffer.writeInt(this.classicBackgroundColor);
    buffer.writeFloat(this.classicBackgroundHue);
    buffer.writeFloat(this.classicBackgroundSaturation);
    buffer.writeFloat(this.classicBackgroundBrightness);
    buffer.writeFloat(this.classicBackgroundAlpha);
    buffer.writeString(this.classicBackgroundMode);
  }

  read(buffer: PacketBuffer): void {
    this.updateMusicVolume = buffer.readBoolean();
    this.musicVolume = buffer.readDouble();
    this.updateAnonymousDataEnabled = buffer.readBoolean();
    this.anonymousDataEnabled = buffer.readBoolean();
    this.updateClientPreferences = buffer.readBoolean();
    this.background = buffer.readString() ?? 'panorama_1';
    this.oobeCompleted = buffer.readBoolean();
    this.antiCheatEnabled = buffer.readBoolean();
    this.classicBackgroundColor = buffer.readInt();
    this.classicBackgroundHue = buffer.readFloat();
    this.classicBackgroundSaturation = buffer.readFloat();
    this.classicBackgroundBrightness = buffer.readFloat();
    this.classicBackgroundAlpha = buffer.readFloat();
    this.classicBackgroundMode = buffer.readString() ?? 'STATIC';
  }
}
