export interface PlayerStatus {
  playerName: string
  dimension: string
  biome: string
  posX: number
  posY: number
  posZ: number
  pokedexCaught: number
  pokedexEncountered: number
  pokedexTotal: number
  pokedexPercentage: number
  partyCount: number
}

export interface PokemonSummary {
  nationalDexNumber: number
  speciesId: string
  name: string
  displayName: string
  primaryType: string
  secondaryType?: string | null
  generation: number
  status: 'CAUGHT' | 'ENCOUNTERED' | 'MISSING'
  formsCount: number
  ownedCount: number
  hasPendingEvolution: boolean
  isLegendary: boolean
}

export interface StatInfo {
  name: string
  value: number
  evYield: number
}

export interface MoveInfo {
  id: string
  displayName: string
  type: string
  power: number | null
  accuracy: number | null
  pp: number
  maxPp: number
  currentPp?: number | null
}

export interface FormInfo {
  name: string
  displayName: string
  primaryType: string
  secondaryType?: string | null
  aspects: string[]
}

export interface AbilityInfo {
  name: string
  displayName: string
  isHidden: boolean
  description: string
}

export interface EvolutionNode {
  sourceSpeciesId: string
  sourceName: string
  targetSpeciesId: string
  targetName: string
  requirementsSummary: string
  requiredLevel?: number | null
  requiredItem?: string | null
  isItemTriggered: boolean
}

export interface DropInfo {
  itemId: string
  displayName: string
  minCount: number
  maxCount: number
  chancePercentage: number
}

export interface SpawnZone {
  biomes: string[]
  dimensions: string[]
  timeRange?: string | null
  weather?: string | null
  minLevel: number
  maxLevel: number
  weight: number
  rarityBucket: string
}

export interface PokeSnack {
  optimalTaste: string
  recipeItems: string[]
  description: string
}

export interface PastureJob {
  jobName: string
  description: string
}

export interface OwnedPokemon {
  uuid: string
  speciesId: string
  speciesName: string
  nationalDexNumber: number
  displayName: string
  primaryType?: string
  secondaryType?: string | null
  level: number
  location: string
  isParty: boolean
  boxIndex: number
  slotIndex: number
  isShiny: boolean
  gender: 'MALE' | 'FEMALE' | 'GENDERLESS'
  ability: string
  nature: string
  heldItem?: string | null
  currentHp: number
  maxHp: number
  ivs: Record<string, number>
  evs: Record<string, number>
  moves: MoveInfo[]
}

export interface SpeciesDetail {
  nationalDexNumber: number
  speciesId: string
  name: string
  displayName: string
  primaryType: string
  secondaryType?: string | null
  generation: number
  status: 'CAUGHT' | 'ENCOUNTERED' | 'MISSING'
  height: number
  weight: number
  catchRate: number
  baseFriendship: number
  baseStats: StatInfo[]
  totalStats: number
  abilities: AbilityInfo[]
  forms: FormInfo[]
  evolutions: EvolutionNode[]
  drops: DropInfo[]
  spawns: SpawnZone[]
  pokeSnack?: PokeSnack | null
  pastureJob?: PastureJob | null
  ownedInstances: OwnedPokemon[]
}

export interface EvolutionGoal {
  sourceSpeciesId: string
  sourceSpeciesName?: string
  sourceName: string
  sourceDexNumber: number
  targetSpeciesId: string
  targetSpeciesName?: string
  targetName: string
  targetDexNumber: number
  feasibility: 'READY_TO_EVOLVE' | 'CAN_EVOLVE_WITH_PC' | 'MISSING_BASE_POKEMON'
  requirementsSummary: string
  requiredItems: string[]
  missingItems: string[]
  isItemOwned: boolean
  minLevel?: number | null
  isLevelMet: boolean
  minFriendship?: number | null
  isFriendshipMet: boolean
  ownedLocationSummary?: string | null
  ownedInstancesCount: number
  targetStatus: string
}

export interface BreedingOrigin {
  baseSpeciesId: string
  baseSpeciesName?: string
  baseName: string
  baseDexNumber: number
  isBaby: boolean
  targetStatus: string
  candidateParents: string[]
  ownedParentsSummary: string
  isReadyToBreed: boolean
  requiredIncense?: string | null
}

export interface FossilGoal {
  speciesId: string
  speciesName: string
  nationalDexNumber: number
  fossilName: string
  heldFossilCount: number
  isReadyToRevive: boolean
  isCaught: boolean
  isEncountered: boolean
  associatedStructures: string[]
}

export interface LegendaryGoal {
  speciesId: string
  speciesName: string
  nationalDexNumber: number
  status: 'CAUGHT' | 'ENCOUNTERED' | 'MISSING'
  isCoveredByMonuments: boolean
  monumentStructure?: string | null
}

export interface PcBox {
  boxIndex: number
  boxName: string
  totalSlots: number
  occupiedSlots: number
  pokemons: (OwnedPokemon | null)[]
}

export interface RadarEntity {
  speciesId: string
  displayName: string
  nationalDexNumber: number
  level: number
  distance: number
  relativeYaw: number
  altitudeDiff: number
  isShiny: boolean
  perfectIvsCount: number
  hasHiddenAbility: boolean
  status: 'CAUGHT' | 'ENCOUNTERED' | 'MISSING'
}

export interface ToastMessage {
  id: string
  type: 'capture' | 'evolution' | 'info'
  title: string
  subtitle: string
  icon?: string
  timestamp: number
}
