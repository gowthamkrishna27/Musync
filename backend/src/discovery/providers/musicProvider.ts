import { NormalizedTrack } from "../types";

export interface IMusicProvider {
  readonly providerName: string;

  fetchTrending(region?: string, language?: string, limit?: number): Promise<NormalizedTrack[]>;

  fetchNewReleases(language?: string, limit?: number): Promise<NormalizedTrack[]>;

  fetchRising(limit?: number): Promise<NormalizedTrack[]>;
}
