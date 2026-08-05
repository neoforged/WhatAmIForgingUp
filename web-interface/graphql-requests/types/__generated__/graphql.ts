export type Maybe<T> = T | null;
export type InputMaybe<T> = Maybe<T>;
export type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
export type MakeOptional<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]?: Maybe<T[SubKey]> };
export type MakeMaybe<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]: Maybe<T[SubKey]> };
export type MakeEmpty<T extends { [key: string]: unknown }, K extends keyof T> = { [_ in K]?: never };
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
  ID: { input: string; output: string; }
  String: { input: string; output: string; }
  Boolean: { input: boolean; output: boolean; }
  Int: { input: number; output: number; }
  Float: { input: number; output: number; }
  /** A slightly refined version of RFC-3339 compliant DateTime Scalar */
  DateTime: { input: unknown; output: unknown; }
  /** A JSON scalar */
  JSON: { input: any; output: any; }
};

/** Represents an object that can be annotated - method, field or class definition */
export type Annotatable = {
  /** Returns annotations on this object matching the given predicate */
  annotations: Array<Annotation>;
};


/** Represents an object that can be annotated - method, field or class definition */
export type AnnotatableAnnotationsArgs = {
  where?: InputMaybe<AnnotationPredicate>;
};

/** An annotation instance (the type and its value as a JSON object) */
export type Annotation = {
  __typename?: 'Annotation';
  /** The type of the annotation - e.g. `java/lang/Deprecated` */
  type: Scalars['String']['output'];
  /** The value of this annotation as a JSON object */
  value: Maybe<Scalars['JSON']['output']>;
};

/** Used to filter annotations based on different criteria */
export type AnnotationPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<AnnotationPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<AnnotationPredicate>>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<AnnotationPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<AnnotationPredicate>;
  /** Filter based on the annotation type */
  type?: InputMaybe<StringPredicate>;
  /** Filter based on the annotation value */
  value?: InputMaybe<JsonPredicate>;
};

/** A base interfaces for classes identified by their name (e.g. `java/lang/String`) */
export type BaseClass = {
  /** Returns all known concrete definitions of the class */
  definitions: Array<ClassDefinition>;
  /**
   * The fields of this class, optionally matching the given predicate.
   * Note that it is not guaranteed that the fields returned have a concrete implementation.
   */
  fields: Array<Field>;
  /** The unique ID of this object */
  id: Scalars['Int']['output'];
  /**
   * The methods of this class, optionally matching the given predicate.
   * Note that it is not guaranteed that the methods returned have a concrete implementation.
   */
  methods: Array<Method>;
  /** The name of this class */
  name: Scalars['String']['output'];
};


/** A base interfaces for classes identified by their name (e.g. `java/lang/String`) */
export type BaseClassDefinitionsArgs = {
  order?: InputMaybe<ClassDefinitionOrdering>;
  where?: InputMaybe<ClassDefinitionPredicate>;
};


/** A base interfaces for classes identified by their name (e.g. `java/lang/String`) */
export type BaseClassFieldsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<FieldPredicate>;
};


/** A base interfaces for classes identified by their name (e.g. `java/lang/String`) */
export type BaseClassMethodsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<MethodPredicate>;
};

/** A base interface used to describe the simple properties of an indexed Mod */
export type BaseMod = {
  /** The authors of this mod (derived from mod metadata) */
  authors: Maybe<Scalars['String']['output']>;
  /** The ID of the CurseForge project associated with this mod */
  curseforgeProjectId: Maybe<Scalars['Int']['output']>;
  /** The unique ID of this object */
  id: Scalars['Int']['output'];
  /** The time this mod was last indexed */
  indexedOn: Maybe<Scalars['DateTime']['output']>;
  /** The license of this mod (derived from mod metadata) */
  license: Maybe<Scalars['String']['output']>;
  /** The sections of the manifest of the last jar indexed for this mod */
  manifest: Array<ManifestEntry>;
  /**
   * The maven coordinates of this mod (only set if this mod was JiJ'd by another mod).
   * On Fabric these are not real maven coordinates, rather the ID of the mod if the file is a mod, or if the file is a non-mod library, it will be generated by Loom based on its maven coordinates.
   */
  mavenCoordinates: Maybe<Scalars['String']['output']>;
  /**
   * The metadata of this mod, in JSON format.
   * Can be sourced from one of the following files depending on the loader:
   * - `META-INF/neoforge.mods.toml` for NeoForge
   * - `META-INF/mods.toml` for Forge
   * - `fabric.mod.json` for Fabric
   */
  metadata: Maybe<Scalars['JSON']['output']>;
  /**
   * The mod IDs of this mod.
   * Some loaders only allow a mod to have one ID (Fabric) while others may allow a mod file to have multiple mod IDs.
   * For non-mod files this will be null.
   */
  modIds: Maybe<Array<Scalars['String']['output']>>;
  /** The ID of the Modrinth project associated with this mod */
  modrinthProjectId: Maybe<Scalars['String']['output']>;
  /** The name of this mod (derived from mod metadata, jar contents or maven coordinates) */
  name: Scalars['String']['output'];
  /**
   * The artifacts this mod file nests within itself, using a jar-in-jar system.
   * Note that this is returned as a tree, rather than a flat array containing sub-nested artifacts.
   */
  nestedArtifacts: Maybe<Array<NestedArtifactNode>>;
  /**
   * The artifacts this mod file nests within itself, using a jar-in-jar system.
   * Note that unlike `nestedArtifacts`, this is returned as a flat array, rather than a tree.
   */
  nestedArtifactsFlat: Maybe<Array<NestedArtifact>>;
  /** The last indexed version of this mod */
  version: Scalars['String']['output'];
};


/** A base interface used to describe the simple properties of an indexed Mod */
export type BaseModMetadataArgs = {
  path?: InputMaybe<Scalars['String']['input']>;
};

/** Represents an artifact nested within another one */
export type BaseNestedArtifact = {
  /** The ID of this artifact */
  id: Scalars['String']['output'];
  /** The version of this artifact */
  version: Scalars['String']['output'];
};

/** Used to cast objects within predicates to concrete types (useful for JSONPath extract predicates) */
export type CastingPredicate = {
  /** Cast the object to int */
  int?: InputMaybe<IntPredicate>;
  /** Cast the object to string */
  string?: InputMaybe<StringPredicate>;
};

/** A class identified by its name (e.g. `java/lang/String`) from which an inheritor search can be started. */
export type Class = BaseClass & Identifiable & {
  __typename?: 'Class';
  /** Returns all known concrete definitions of the class */
  definitions: Array<ClassDefinition>;
  /**
   * The fields of this class, optionally matching the given predicate.
   * Note that it is not guaranteed that the fields returned have a concrete implementation.
   */
  fields: Array<Field>;
  /** The unique ID of this object */
  id: Scalars['Int']['output'];
  /** Returns all known classes that inherit from this class and which optionally match the given filter. */
  inheritors: Array<InheritanceTreeClass>;
  /**
   * The methods of this class, optionally matching the given predicate.
   * Note that it is not guaranteed that the methods returned have a concrete implementation.
   */
  methods: Array<Method>;
  /** The name of this class */
  name: Scalars['String']['output'];
};


/** A class identified by its name (e.g. `java/lang/String`) from which an inheritor search can be started. */
export type ClassDefinitionsArgs = {
  order?: InputMaybe<ClassDefinitionOrdering>;
  where?: InputMaybe<ClassDefinitionPredicate>;
};


/** A class identified by its name (e.g. `java/lang/String`) from which an inheritor search can be started. */
export type ClassFieldsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<FieldPredicate>;
};


/** A class identified by its name (e.g. `java/lang/String`) from which an inheritor search can be started. */
export type ClassInheritorsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  order?: InputMaybe<InheritanceTreeClassOrdering>;
  where?: InputMaybe<InheritorPredicate>;
};


/** A class identified by its name (e.g. `java/lang/String`) from which an inheritor search can be started. */
export type ClassMethodsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<MethodPredicate>;
};

/** A connection (list) composed of `Class`. */
export type ClassConnection = {
  __typename?: 'ClassConnection';
  /** Identifies the amount of items in the returned edges. */
  count: Scalars['Int']['output'];
  /** A list of edges. */
  edges: Array<ClassEdge>;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
};

/** A concrete definition of a class */
export type ClassDefinition = Annotatable & ModOwned & {
  __typename?: 'ClassDefinition';
  /** Returns annotations on this object matching the given predicate */
  annotations: Array<Annotation>;
  /**
   * The fields this class defines directly.
   * Note that this list is not complete and only contains fields relevant for API usage. Therefore private fields that have no annotations are not tracked.
   */
  fields: Array<FieldDefinition>;
  /**
   * The methods this class defines directly.
   * Note that this list is not complete and only contains methods relevant for API usage. Therefore private methods that have no annotations are not tracked.
   */
  methods: Array<MethodDefinition>;
  /** The mod that owns this object */
  mod: LightweightMod;
  /** The name of this class */
  name: Scalars['String']['output'];
  /** Classes this class directly inherits from (both super classes and interfaces) */
  parents: Maybe<Array<Scalars['String']['output']>>;
  /**
   * The fields this class references.
   * Note that this list is not complete and only contains references relevant for API usage. Therefore references to private or static fields of classes in the same mod are not tracked.
   */
  referencedFields: Array<ReferencedField>;
  /**
   * The methods this class references.
   * Note that this list is not complete and only contains references relevant for API usage. Therefore references to private, static or non-overriding methods of classes in the same mod are not tracked.
   */
  referencedMethods: Array<ReferencedMethod>;
};


/** A concrete definition of a class */
export type ClassDefinitionAnnotationsArgs = {
  where?: InputMaybe<AnnotationPredicate>;
};


/** A concrete definition of a class */
export type ClassDefinitionFieldsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  order?: InputMaybe<FieldDefinitionOrdering>;
  where?: InputMaybe<FieldPredicate>;
};


/** A concrete definition of a class */
export type ClassDefinitionMethodsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  order?: InputMaybe<MethodDefinitionOrdering>;
  where?: InputMaybe<MethodPredicate>;
};

/** A connection (list) composed of `ClassDefinition`. */
export type ClassDefinitionConnection = {
  __typename?: 'ClassDefinitionConnection';
  /** Identifies the amount of items in the returned edges. */
  count: Scalars['Int']['output'];
  /** A list of edges. */
  edges: Array<ClassDefinitionEdge>;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
};

/** An edge of a `ClassDefinition`. */
export type ClassDefinitionEdge = {
  __typename?: 'ClassDefinitionEdge';
  /** The cursor ID of the element. */
  cursor: Scalars['ID']['output'];
  /** The item at the end of the edge. */
  node: ClassDefinition;
};

/** Type used to order `ClassDefinition`s */
export type ClassDefinitionOrdering = {
  /** Order by name */
  name?: InputMaybe<OrderMethod>;
};

/** Used to filter class definitions based on different criteria */
export type ClassDefinitionPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<ClassDefinitionPredicate>>;
  /** A class definition will match if any of its annotations match the given predicate */
  anyAnnotation?: InputMaybe<AnnotationPredicate>;
  /** A class definition will match if any of the fields it defines matches the given predicate */
  anyField?: InputMaybe<FieldDefinitionPredicate>;
  /** A class definition will match if any of the methods it defines matches the given predicate */
  anyMethod?: InputMaybe<MethodDefinitionPredicate>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<ClassDefinitionPredicate>>;
  /** A class definition will match if any of its <b>direct</b> parents match the given predicate */
  anyParent?: InputMaybe<StringPredicate>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** A class definition will match if the mod that defines it matches the given predicate */
  mod?: InputMaybe<ModPredicate>;
  /** Filter based on the class' JVM name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<ClassDefinitionPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<ClassDefinitionPredicate>;
};

/** An edge of a `Class`. */
export type ClassEdge = {
  __typename?: 'ClassEdge';
  /** The cursor ID of the element. */
  cursor: Scalars['ID']['output'];
  /** The item at the end of the edge. */
  node: Class;
};

/** Used to filter classes based on different criteria */
export type ClassPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<ClassPredicate>>;
  /** A class will match if any of its fields match the given predicate */
  anyField?: InputMaybe<FieldPredicate>;
  /** A class will match if any of its methods match the given predicate */
  anyMethod?: InputMaybe<MethodPredicate>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<ClassPredicate>>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the class' JVM name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<ClassPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<ClassPredicate>;
};

/** An arbitrary JSON data file */
export type DataFile = ModOwned & {
  __typename?: 'DataFile';
  /** The mod that owns this object */
  mod: LightweightMod;
  /** The name of this file (e.g. `mymod:my/file` for a file at `data/mymod/somepath/my/file.json`, with the base path of the query being `somepath`) */
  name: Scalars['String']['output'];
  /** The contents of the data file. */
  value: Scalars['JSON']['output'];
};

/** A connection (list) composed of `DataFile`. */
export type DataFileConnection = {
  __typename?: 'DataFileConnection';
  /** Identifies the amount of items in the returned edges. */
  count: Scalars['Int']['output'];
  /** A list of edges. */
  edges: Array<DataFileEdge>;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
};

/** An edge of a `DataFile`. */
export type DataFileEdge = {
  __typename?: 'DataFileEdge';
  /** The cursor ID of the element. */
  cursor: Scalars['ID']['output'];
  /** The item at the end of the edge. */
  node: DataFile;
};

/** Used to filter data files based on different criteria */
export type DataFilePredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<DataFilePredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<DataFilePredicate>>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the data file name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<DataFilePredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<DataFilePredicate>;
  /** Filter based on the data file JSON contents */
  value?: InputMaybe<JsonPredicate>;
};

/** A NeoForge data map */
export type DataMap = {
  __typename?: 'DataMap';
  /** The entries in this file */
  entries: Array<DataMapEntry>;
  /** The name of this data map (e.g. `neoforge:compostables`) */
  name: Scalars['String']['output'];
};

/** A connection (list) composed of `DataMap`. */
export type DataMapConnection = {
  __typename?: 'DataMapConnection';
  /** Identifies the amount of items in the returned edges. */
  count: Scalars['Int']['output'];
  /** A list of edges. */
  edges: Array<DataMapEdge>;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
};

/** An edge of a `DataMap`. */
export type DataMapEdge = {
  __typename?: 'DataMapEdge';
  /** The cursor ID of the element. */
  cursor: Scalars['ID']['output'];
  /** The item at the end of the edge. */
  node: DataMap;
};

/** An entry in a NeoForge data map */
export type DataMapEntry = {
  __typename?: 'DataMapEntry';
  /** The key of this entry. Can either be the location of a registry object (e.g. `minecraft:coal_block`) or a tag, denoted by a # at the start (e.g. `#minecraft:logs`) */
  key: Scalars['String']['output'];
  /** The values assigned to this entry by different mods. */
  values: Array<DataMapValue>;
};

/** A NeoForge data map file */
export type DataMapFile = {
  __typename?: 'DataMapFile';
  /** The entries in this file */
  entries: Array<DataMapFileEntry>;
  /** The name of this data map (e.g. `neoforge:compostables`) */
  name: Scalars['String']['output'];
};

/** An entry in a NeoForge data map file */
export type DataMapFileEntry = {
  __typename?: 'DataMapFileEntry';
  /** The key of this entry. Can either be the location of a registry object (e.g. `minecraft:coal_block`) or a tag, denoted by a # at the start (e.g. `#minecraft:logs`) */
  key: Scalars['String']['output'];
  /**
   * Whether this entry replaces previously added entries for objects within the key without invoking mergers.
   * Corresponds to the "replace" field at either the top of the data map file or within individual entries.
   */
  replace: Scalars['Boolean']['output'];
  /** The value of this entry - i.e. the object attached to the entry with the `key` ID or to entries within the tag, if the `key` is a tag */
  value: Scalars['JSON']['output'];
};

/** Used to filter data map files based on different criteria */
export type DataMapFilePredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<DataMapFilePredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<DataMapFilePredicate>>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the data map name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<DataMapFilePredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<DataMapFilePredicate>;
};

/** Used to filter data maps based on different criteria */
export type DataMapPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<DataMapPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<DataMapPredicate>>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the data map name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<DataMapPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<DataMapPredicate>;
};

/** A value within a NeoForge data map, associated with an entry */
export type DataMapValue = ModOwned & {
  __typename?: 'DataMapValue';
  /** The mod that owns this object */
  mod: LightweightMod;
  /** The value of this entry - i.e. the object attached to the entry with the `key` ID or to entries within the tag, if the `key` is a tag */
  value: Scalars['JSON']['output'];
};

/** A predicate applied to date types */
export type DatePredicate = {
  /** Matches if the date is after (greater than) the given date */
  after?: InputMaybe<Scalars['DateTime']['input']>;
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<DatePredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<DatePredicate>>;
  /** Matches if the date is before (smaller than) the given date */
  before?: InputMaybe<Scalars['DateTime']['input']>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<DatePredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<DatePredicate>;
};

/** A NeoForge enum extension entry */
export type EnumExtension = ModOwned & {
  __typename?: 'EnumExtension';
  /** The descriptor of the constructor this extension uses */
  constructor: Scalars['String']['output'];
  /** The internal name of the enum this extension targets */
  enum: Scalars['String']['output'];
  /** The mod that owns this object */
  mod: LightweightMod;
  /** The name of the enum field this extension adds */
  name: Scalars['String']['output'];
  /** The source of the parameters */
  parameters: Scalars['JSON']['output'];
};

/** A connection (list) composed of `EnumExtension`. */
export type EnumExtensionConnection = {
  __typename?: 'EnumExtensionConnection';
  /** Identifies the amount of items in the returned edges. */
  count: Scalars['Int']['output'];
  /** A list of edges. */
  edges: Array<EnumExtensionEdge>;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
};

/** An edge of a `EnumExtension`. */
export type EnumExtensionEdge = {
  __typename?: 'EnumExtensionEdge';
  /** The cursor ID of the element. */
  cursor: Scalars['ID']['output'];
  /** The item at the end of the edge. */
  node: EnumExtension;
};

/** Used to filter enum extension entries based on different criteria */
export type EnumExtensionPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<EnumExtensionPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<EnumExtensionPredicate>>;
  /** Filter based on the enum constructor the extension uses */
  constructor?: InputMaybe<StringPredicate>;
  /** Filter based on the internal name of the enum the extension is for */
  enum?: InputMaybe<StringPredicate>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the mod that defines this enum extension */
  mod?: InputMaybe<ModPredicate>;
  /** Filter based on the name of the enum value the extension adds */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<EnumExtensionPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<EnumExtensionPredicate>;
};

/** Represents a method as returned by `Class.fields` */
export type Field = Identifiable & Referenceable & {
  __typename?: 'Field';
  /** The unique ID of this object */
  id: Scalars['Int']['output'];
  /** The name of this field */
  name: Scalars['String']['output'];
  /** Returns the references to this element */
  references: Array<Reference>;
  /**
   * The type of this field
   * Can be a JVM internal class like like `java/lang/Object` or a type descriptor like `I`, `Z` or `[Ljava/lang/Object;`.
   */
  type: Scalars['String']['output'];
};


/** Represents a method as returned by `Class.fields` */
export type FieldReferencesArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  order?: InputMaybe<ReferenceOrdering>;
  where?: InputMaybe<ReferencePredicate>;
};

/** A concrete definition of a field */
export type FieldDefinition = Annotatable & {
  __typename?: 'FieldDefinition';
  /** Returns annotations on this object matching the given predicate */
  annotations: Array<Annotation>;
  /** The name of this method */
  name: Scalars['String']['output'];
  /** The type of this field. This is not a descriptor so classes are in internal JVM format (e.g. `java/lang/String` instead of `Ljava/lang/String;`) */
  type: Scalars['String']['output'];
};


/** A concrete definition of a field */
export type FieldDefinitionAnnotationsArgs = {
  where?: InputMaybe<AnnotationPredicate>;
};

/** Type used to order `FieldDefinition`s */
export type FieldDefinitionOrdering = {
  /** Order by name */
  name?: InputMaybe<OrderMethod>;
  /** Order by type */
  type?: InputMaybe<OrderMethod>;
};

/** Used to filter field definitions based on different criteria */
export type FieldDefinitionPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<FieldDefinitionPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<FieldDefinitionPredicate>>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the field's name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<FieldDefinitionPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<FieldDefinitionPredicate>;
  /** Filter based on the field's type */
  type?: InputMaybe<StringPredicate>;
};

/** Used to filter fields based on different criteria */
export type FieldPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<FieldPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<FieldPredicate>>;
  /** A field will match if any of the (direct) references to it match the given predicate */
  anyReference?: InputMaybe<ReferencePredicate>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the field's name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<FieldPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<FieldPredicate>;
  /** Filter based on the field's type */
  type?: InputMaybe<StringPredicate>;
};

/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersion = {
  __typename?: 'GameVersion';
  /**
   * Internal endpoint. It might be removed at any point
   * @deprecated No longer supported
   */
  _modInformation: IntModInformation;
  /**
   * Get the class with the given `name`. If no such class exists in this game version, this will return `null`.
   * In contrast to `GameVersion.classes`, this can only query one class by an exact name match, being easier to use and slightly more efficient.
   */
  class: Maybe<Class>;
  /** Get the class definitions known by this game version that optionally match the given predicate */
  classDefinitions: ClassDefinitionConnection;
  /** Get the classes known by this game version that optionally match the given predicate */
  classes: ClassConnection;
  /**
   * Get the data files known by this game version for the given `location` that optionally match the given predicate.
   * Note that this will not return files already indexed through other means (i.e. tags, data maps and recipes). Use the dedicated fields to query those.
   */
  dataFiles: DataFileConnection;
  /**
   * Get the data maps known by this game version that optionally match the given predicate.
   * This returns a different data structure compared to `Mod.dataMaps`, allowing you to query each value assigned to each object
   * and the mod that does so.
   */
  dataMaps: DataMapConnection;
  /** Get the enum extensions known by this game version that optionally match the given predicate */
  enumExtensions: EnumExtensionConnection;
  /** The loader this game version indexes */
  loader: Loader;
  /** Query mods from the database */
  mods: ModConnection;
  /**
   * Get the mods from the database that have the given IDs.
   * Note that the mods are not returned in a specific order relative to the input IDs.
   */
  modsById: ModConnection;
  /** Get the recipe files known by this game version that optionally match the given predicate */
  recipes: RecipeFileConnection;
  /** Get the tag entries known by this game version for the given `registry` that optionally match the given predicate. */
  tagEntries: TagEntryConnection;
  /** The Minecraft version this game version indexes */
  version: Scalars['String']['output'];
};


/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersion_ModInformationArgs = {
  id: Scalars['Int']['input'];
};


/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersionClassArgs = {
  name?: InputMaybe<Scalars['String']['input']>;
};


/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersionClassDefinitionsArgs = {
  after?: InputMaybe<Scalars['ID']['input']>;
  before?: InputMaybe<Scalars['ID']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<ClassDefinitionPredicate>;
};


/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersionClassesArgs = {
  after?: InputMaybe<Scalars['ID']['input']>;
  before?: InputMaybe<Scalars['ID']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<ClassPredicate>;
};


/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersionDataFilesArgs = {
  after?: InputMaybe<Scalars['ID']['input']>;
  before?: InputMaybe<Scalars['ID']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  location: Scalars['String']['input'];
  where?: InputMaybe<DataFilePredicate>;
};


/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersionDataMapsArgs = {
  after?: InputMaybe<Scalars['ID']['input']>;
  before?: InputMaybe<Scalars['ID']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  registry: Scalars['String']['input'];
  where?: InputMaybe<DataMapPredicate>;
};


/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersionEnumExtensionsArgs = {
  after?: InputMaybe<Scalars['ID']['input']>;
  before?: InputMaybe<Scalars['ID']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<EnumExtensionPredicate>;
};


/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersionModsArgs = {
  after?: InputMaybe<Scalars['ID']['input']>;
  before?: InputMaybe<Scalars['ID']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<ModPredicate>;
};


/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersionModsByIdArgs = {
  after?: InputMaybe<Scalars['ID']['input']>;
  before?: InputMaybe<Scalars['ID']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  ids: Array<Scalars['Int']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
};


/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersionRecipesArgs = {
  after?: InputMaybe<Scalars['ID']['input']>;
  before?: InputMaybe<Scalars['ID']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<RecipeFilePredicate>;
};


/** Represents a version of the game on a loader, which WAIFU is indexing */
export type GameVersionTagEntriesArgs = {
  after?: InputMaybe<Scalars['ID']['input']>;
  before?: InputMaybe<Scalars['ID']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  registry: Scalars['String']['input'];
  where?: InputMaybe<TagEntryPredicate>;
};

/** Represents an object that is identifiable by an unique ID (within its category) */
export type Identifiable = {
  /** The unique ID of this object */
  id: Scalars['Int']['output'];
};

/** A class identified by its name (e.g. `java/lang/String`) as returned by `Class.inheritors` which has a certain depth from the root of the inheritance tree. */
export type InheritanceTreeClass = BaseClass & Identifiable & {
  __typename?: 'InheritanceTreeClass';
  /** Returns all known concrete definitions of the class */
  definitions: Array<ClassDefinition>;
  /**
   * The depth of this class in the inheritance tree.
   * Classes with a depth of 1 are direct children of the class from which the search was started, classes with a depth of 2 are their children and so on.
   */
  depth: Scalars['Int']['output'];
  /**
   * The fields of this class, optionally matching the given predicate.
   * Note that it is not guaranteed that the fields returned have a concrete implementation.
   */
  fields: Array<Field>;
  /** The unique ID of this object */
  id: Scalars['Int']['output'];
  /**
   * The methods of this class, optionally matching the given predicate.
   * Note that it is not guaranteed that the methods returned have a concrete implementation.
   */
  methods: Array<Method>;
  /** The name of this class */
  name: Scalars['String']['output'];
};


/** A class identified by its name (e.g. `java/lang/String`) as returned by `Class.inheritors` which has a certain depth from the root of the inheritance tree. */
export type InheritanceTreeClassDefinitionsArgs = {
  order?: InputMaybe<ClassDefinitionOrdering>;
  where?: InputMaybe<ClassDefinitionPredicate>;
};


/** A class identified by its name (e.g. `java/lang/String`) as returned by `Class.inheritors` which has a certain depth from the root of the inheritance tree. */
export type InheritanceTreeClassFieldsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<FieldPredicate>;
};


/** A class identified by its name (e.g. `java/lang/String`) as returned by `Class.inheritors` which has a certain depth from the root of the inheritance tree. */
export type InheritanceTreeClassMethodsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<MethodPredicate>;
};

/** Type used to order `InheritanceTreeClass`s */
export type InheritanceTreeClassOrdering = {
  /** Order by depth */
  depth?: InputMaybe<OrderMethod>;
};

/** Used to filter inheriting classes based on different criteria */
export type InheritorPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<InheritorPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<InheritorPredicate>>;
  /** Filter based on the class' depth in the inheritance tree */
  depth?: InputMaybe<IntPredicate>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the class' JVM name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<InheritorPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<InheritorPredicate>;
};

export type IntModInformation = BaseMod & Identifiable & {
  __typename?: 'IntModInformation';
  /** The authors of this mod (derived from mod metadata) */
  authors: Maybe<Scalars['String']['output']>;
  curseforge: Maybe<IntPlatformInformation>;
  /** The ID of the CurseForge project associated with this mod */
  curseforgeProjectId: Maybe<Scalars['Int']['output']>;
  /** The unique ID of this object */
  id: Scalars['Int']['output'];
  /** The time this mod was last indexed */
  indexedOn: Maybe<Scalars['DateTime']['output']>;
  /** The license of this mod (derived from mod metadata) */
  license: Maybe<Scalars['String']['output']>;
  /** The sections of the manifest of the last jar indexed for this mod */
  manifest: Array<ManifestEntry>;
  /**
   * The maven coordinates of this mod (only set if this mod was JiJ'd by another mod).
   * On Fabric these are not real maven coordinates, rather the ID of the mod if the file is a mod, or if the file is a non-mod library, it will be generated by Loom based on its maven coordinates.
   */
  mavenCoordinates: Maybe<Scalars['String']['output']>;
  /**
   * The metadata of this mod, in JSON format.
   * Can be sourced from one of the following files depending on the loader:
   * - `META-INF/neoforge.mods.toml` for NeoForge
   * - `META-INF/mods.toml` for Forge
   * - `fabric.mod.json` for Fabric
   */
  metadata: Maybe<Scalars['JSON']['output']>;
  /**
   * The mod IDs of this mod.
   * Some loaders only allow a mod to have one ID (Fabric) while others may allow a mod file to have multiple mod IDs.
   * For non-mod files this will be null.
   */
  modIds: Maybe<Array<Scalars['String']['output']>>;
  modrinth: Maybe<IntPlatformInformation>;
  /** The ID of the Modrinth project associated with this mod */
  modrinthProjectId: Maybe<Scalars['String']['output']>;
  /** The name of this mod (derived from mod metadata, jar contents or maven coordinates) */
  name: Scalars['String']['output'];
  /**
   * The artifacts this mod file nests within itself, using a jar-in-jar system.
   * Note that this is returned as a tree, rather than a flat array containing sub-nested artifacts.
   */
  nestedArtifacts: Maybe<Array<NestedArtifactNode>>;
  /**
   * The artifacts this mod file nests within itself, using a jar-in-jar system.
   * Note that unlike `nestedArtifacts`, this is returned as a flat array, rather than a tree.
   */
  nestedArtifactsFlat: Maybe<Array<NestedArtifact>>;
  /** The last indexed version of this mod */
  version: Scalars['String']['output'];
};


export type IntModInformationMetadataArgs = {
  path?: InputMaybe<Scalars['String']['input']>;
};

export type IntPlatformInformation = {
  __typename?: 'IntPlatformInformation';
  description: Scalars['String']['output'];
  downloads: Scalars['Int']['output'];
  fileDownloadUrl: Maybe<Scalars['String']['output']>;
  iconUrl: Scalars['String']['output'];
  issuesUrl: Maybe<Scalars['String']['output']>;
  projectUrl: Scalars['String']['output'];
  sourceUrl: Maybe<Scalars['String']['output']>;
  title: Scalars['String']['output'];
};


export type IntPlatformInformationFileDownloadUrlArgs = {
  loader: Loader;
  version: Scalars['String']['input'];
};

/**
 * Represents a way of filtering numbers based on different rules that are either logical operators
 * on other filters (NOT, AND, OR) or number comparisons.
 */
export type IntPredicate = {
  /** The number being filtered will pass this predicate if its absolute value matches the given predicate. */
  abs?: InputMaybe<IntPredicate>;
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<IntPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<IntPredicate>>;
  /** The number being filtered must be equal to the given number. */
  equals?: InputMaybe<Scalars['Int']['input']>;
  /** The number being filtered must be greater than the given number. */
  greaterThan?: InputMaybe<Scalars['Int']['input']>;
  /** The number being filtered must be greater than or equal to the given number. */
  greaterThanOrEqual?: InputMaybe<Scalars['Int']['input']>;
  /** The number being filtered must be even if this is set to true, or odd otherwise. */
  isEven?: InputMaybe<Scalars['Boolean']['input']>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** The number being filtered must be less than the given number. */
  lessThan?: InputMaybe<Scalars['Int']['input']>;
  /** The number being filtered must be less than or equal to the given number. */
  lessThanOrEqual?: InputMaybe<Scalars['Int']['input']>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<IntPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<IntPredicate>;
};

/**
 * Predicate used to test objects extracted from a path within a JSON object. For instance, given the following extract predicate:
 * ```json
 * {
 * path: "$.mods[*].id",
 * as: {
 * string: {
 * startsWith: "abc_"
 * }
 * }
 * }
 * ```
 * will match JSON objects that have an `id` element within any element of the top-level `mods` array that, as a string, starts with `abc_`.
 */
export type JsonExtractPredicate = {
  /** Cast the extracted object to the given type and check it against the given predicate */
  as: CastingPredicate;
  /** The path to extract from */
  path: Scalars['String']['input'];
};

/**
 * Represents a way of filtering JSON values based on different rules that are either logical operators
 * on other filters (NOT, AND, OR) or JSON queries.
 */
export type JsonPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<JsonPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<JsonPredicate>>;
  /** A JSON value will match if at least one object extracted from the given path matches the given predicate */
  extract?: InputMaybe<JsonExtractPredicate>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<JsonPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<JsonPredicate>;
  /** A JSON value will match this predicate if at least a value matching the given JSONPath exists */
  pathExists?: InputMaybe<Scalars['String']['input']>;
};

/**
 * A mod instance which has only basic properties available (properties that do not require joining on tables other than `mods`).
 * These are returned by objects associated with a mod (like class definitions).
 * To "upgrade" a lightweight mod to a _Mod_ you can use `modsById`.
 */
export type LightweightMod = BaseMod & Identifiable & {
  __typename?: 'LightweightMod';
  /** The authors of this mod (derived from mod metadata) */
  authors: Maybe<Scalars['String']['output']>;
  /** The ID of the CurseForge project associated with this mod */
  curseforgeProjectId: Maybe<Scalars['Int']['output']>;
  /** The unique ID of this object */
  id: Scalars['Int']['output'];
  /** The time this mod was last indexed */
  indexedOn: Maybe<Scalars['DateTime']['output']>;
  /** The license of this mod (derived from mod metadata) */
  license: Maybe<Scalars['String']['output']>;
  /** The sections of the manifest of the last jar indexed for this mod */
  manifest: Array<ManifestEntry>;
  /**
   * The maven coordinates of this mod (only set if this mod was JiJ'd by another mod).
   * On Fabric these are not real maven coordinates, rather the ID of the mod if the file is a mod, or if the file is a non-mod library, it will be generated by Loom based on its maven coordinates.
   */
  mavenCoordinates: Maybe<Scalars['String']['output']>;
  /**
   * The metadata of this mod, in JSON format.
   * Can be sourced from one of the following files depending on the loader:
   * - `META-INF/neoforge.mods.toml` for NeoForge
   * - `META-INF/mods.toml` for Forge
   * - `fabric.mod.json` for Fabric
   */
  metadata: Maybe<Scalars['JSON']['output']>;
  /**
   * The mod IDs of this mod.
   * Some loaders only allow a mod to have one ID (Fabric) while others may allow a mod file to have multiple mod IDs.
   * For non-mod files this will be null.
   */
  modIds: Maybe<Array<Scalars['String']['output']>>;
  /** The ID of the Modrinth project associated with this mod */
  modrinthProjectId: Maybe<Scalars['String']['output']>;
  /** The name of this mod (derived from mod metadata, jar contents or maven coordinates) */
  name: Scalars['String']['output'];
  /**
   * The artifacts this mod file nests within itself, using a jar-in-jar system.
   * Note that this is returned as a tree, rather than a flat array containing sub-nested artifacts.
   */
  nestedArtifacts: Maybe<Array<NestedArtifactNode>>;
  /**
   * The artifacts this mod file nests within itself, using a jar-in-jar system.
   * Note that unlike `nestedArtifacts`, this is returned as a flat array, rather than a tree.
   */
  nestedArtifactsFlat: Maybe<Array<NestedArtifact>>;
  /** The last indexed version of this mod */
  version: Scalars['String']['output'];
};


/**
 * A mod instance which has only basic properties available (properties that do not require joining on tables other than `mods`).
 * These are returned by objects associated with a mod (like class definitions).
 * To "upgrade" a lightweight mod to a _Mod_ you can use `modsById`.
 */
export type LightweightModMetadataArgs = {
  path?: InputMaybe<Scalars['String']['input']>;
};

/** Represents the loaders WAIFU supports (the loaders it can index) */
export enum Loader {
  /** The NeoForge loader (https://github.com/FabricMC) */
  Fabric = 'Fabric',
  /** The Forge loader (https://github.com/MinecraftForge) */
  Forge = 'Forge',
  /** The NeoForge loader (https://github.com/NeoForged) */
  NeoForge = 'NeoForge'
}

/** Represents a manifest attribute (e.g. 'Implementation-Version: 1.0') */
export type ManifestAttribute = {
  __typename?: 'ManifestAttribute';
  /** The key of this attribute */
  key: Scalars['String']['output'];
  /** The value of this attribute */
  value: Scalars['String']['output'];
};

/** Used to filter manifest attributes based on their name and/or value */
export type ManifestAttributePredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<ManifestAttributePredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<ManifestAttributePredicate>>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the attribute name (key) */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<ManifestAttributePredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<ManifestAttributePredicate>;
  /** Filter based on the attribute value */
  value?: InputMaybe<StringPredicate>;
};

/** Represents a manifest entry (section) */
export type ManifestEntry = {
  __typename?: 'ManifestEntry';
  /** The attributes in this section */
  attributes: Array<ManifestAttribute>;
  /** The name of this manifest section (the main section has this field blank) */
  name: Scalars['String']['output'];
};

/** Represents a method as returned by `Class.methods` */
export type Method = Identifiable & Referenceable & {
  __typename?: 'Method';
  /** The descriptor of this method */
  descriptor: Scalars['String']['output'];
  /** The unique ID of this object */
  id: Scalars['Int']['output'];
  /** The name of this method */
  name: Scalars['String']['output'];
  /** Returns the references to this element */
  references: Array<Reference>;
};


/** Represents a method as returned by `Class.methods` */
export type MethodReferencesArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  order?: InputMaybe<ReferenceOrdering>;
  where?: InputMaybe<ReferencePredicate>;
};

/** A concrete definition of a method */
export type MethodDefinition = Annotatable & {
  __typename?: 'MethodDefinition';
  /** Returns annotations on this object matching the given predicate */
  annotations: Array<Annotation>;
  /** The descriptor of this method */
  descriptor: Scalars['String']['output'];
  /** The name of this method */
  name: Scalars['String']['output'];
};


/** A concrete definition of a method */
export type MethodDefinitionAnnotationsArgs = {
  where?: InputMaybe<AnnotationPredicate>;
};

/** Type used to order `MethodDefinition`s */
export type MethodDefinitionOrdering = {
  /** Order by descriptor */
  descriptor?: InputMaybe<OrderMethod>;
  /** Order by name */
  name?: InputMaybe<OrderMethod>;
};

/** Used to filter method definitions based on different criteria */
export type MethodDefinitionPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<MethodDefinitionPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<MethodDefinitionPredicate>>;
  /** Filter based on the method's descriptor */
  descriptor?: InputMaybe<StringPredicate>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the method's name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<MethodDefinitionPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<MethodDefinitionPredicate>;
};

/** Used to filter methods based on different criteria */
export type MethodPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<MethodPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<MethodPredicate>>;
  /** A method will match if any of the (direct) references to it match the given predicate */
  anyReference?: InputMaybe<ReferencePredicate>;
  /** Filter based on the method's descriptor */
  descriptor?: InputMaybe<StringPredicate>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the method's name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<MethodPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<MethodPredicate>;
};

/** Represents a full mod from which data that requires more complex processing can be queries (classes, tags etc.) */
export type Mod = BaseMod & Identifiable & {
  __typename?: 'Mod';
  /** The authors of this mod (derived from mod metadata) */
  authors: Maybe<Scalars['String']['output']>;
  /** Get the classes this mod defines. Optionally takes in a filter on the class name */
  classes: Array<ClassDefinition>;
  /** The ID of the CurseForge project associated with this mod */
  curseforgeProjectId: Maybe<Scalars['Int']['output']>;
  /**
   * Get the data files this mod defines for the given `location`.
   * Note that this will not return files already indexed through other means (i.e. tags, data maps and recipes). Use the dedicated fields to query those.
   */
  dataFiles: Array<DataFile>;
  /** Get the data maps this mod defines for the given registry */
  dataMaps: Array<DataMapFile>;
  /** Get the enum extensions this mod defines */
  enumExtensions: Array<EnumExtension>;
  /** The unique ID of this object */
  id: Scalars['Int']['output'];
  /** The time this mod was last indexed */
  indexedOn: Maybe<Scalars['DateTime']['output']>;
  /** The license of this mod (derived from mod metadata) */
  license: Maybe<Scalars['String']['output']>;
  /** The sections of the manifest of the last jar indexed for this mod */
  manifest: Array<ManifestEntry>;
  /**
   * The maven coordinates of this mod (only set if this mod was JiJ'd by another mod).
   * On Fabric these are not real maven coordinates, rather the ID of the mod if the file is a mod, or if the file is a non-mod library, it will be generated by Loom based on its maven coordinates.
   */
  mavenCoordinates: Maybe<Scalars['String']['output']>;
  /**
   * The metadata of this mod, in JSON format.
   * Can be sourced from one of the following files depending on the loader:
   * - `META-INF/neoforge.mods.toml` for NeoForge
   * - `META-INF/mods.toml` for Forge
   * - `fabric.mod.json` for Fabric
   */
  metadata: Maybe<Scalars['JSON']['output']>;
  /**
   * The mod IDs of this mod.
   * Some loaders only allow a mod to have one ID (Fabric) while others may allow a mod file to have multiple mod IDs.
   * For non-mod files this will be null.
   */
  modIds: Maybe<Array<Scalars['String']['output']>>;
  /** The ID of the Modrinth project associated with this mod */
  modrinthProjectId: Maybe<Scalars['String']['output']>;
  /** The name of this mod (derived from mod metadata, jar contents or maven coordinates) */
  name: Scalars['String']['output'];
  /**
   * The artifacts this mod file nests within itself, using a jar-in-jar system.
   * Note that this is returned as a tree, rather than a flat array containing sub-nested artifacts.
   */
  nestedArtifacts: Maybe<Array<NestedArtifactNode>>;
  /**
   * The artifacts this mod file nests within itself, using a jar-in-jar system.
   * Note that unlike `nestedArtifacts`, this is returned as a flat array, rather than a tree.
   */
  nestedArtifactsFlat: Maybe<Array<NestedArtifact>>;
  /** Get the recipes this mod defines */
  recipes: Array<RecipeFile>;
  /** Get the tags this mod defines for the given registry */
  tags: Array<TagFile>;
  /** The last indexed version of this mod */
  version: Scalars['String']['output'];
};


/** Represents a full mod from which data that requires more complex processing can be queries (classes, tags etc.) */
export type ModClassesArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  order?: InputMaybe<ClassDefinitionOrdering>;
  where?: InputMaybe<ClassDefinitionPredicate>;
};


/** Represents a full mod from which data that requires more complex processing can be queries (classes, tags etc.) */
export type ModDataFilesArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  location?: InputMaybe<Scalars['String']['input']>;
  where?: InputMaybe<DataFilePredicate>;
};


/** Represents a full mod from which data that requires more complex processing can be queries (classes, tags etc.) */
export type ModDataMapsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  registry: Scalars['String']['input'];
  where?: InputMaybe<DataMapFilePredicate>;
};


/** Represents a full mod from which data that requires more complex processing can be queries (classes, tags etc.) */
export type ModEnumExtensionsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<EnumExtensionPredicate>;
};


/** Represents a full mod from which data that requires more complex processing can be queries (classes, tags etc.) */
export type ModMetadataArgs = {
  path?: InputMaybe<Scalars['String']['input']>;
};


/** Represents a full mod from which data that requires more complex processing can be queries (classes, tags etc.) */
export type ModRecipesArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  where?: InputMaybe<RecipeFilePredicate>;
};


/** Represents a full mod from which data that requires more complex processing can be queries (classes, tags etc.) */
export type ModTagsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  registry: Scalars['String']['input'];
  where?: InputMaybe<TagFilePredicate>;
};

/** A connection (list) composed of `Mod`. */
export type ModConnection = {
  __typename?: 'ModConnection';
  /** Identifies the amount of items in the returned edges. */
  count: Scalars['Int']['output'];
  /** A list of edges. */
  edges: Array<ModEdge>;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
};

/** An edge of a `Mod`. */
export type ModEdge = {
  __typename?: 'ModEdge';
  /** The cursor ID of the element. */
  cursor: Scalars['ID']['output'];
  /** The item at the end of the edge. */
  node: Mod;
};

/**
 * Base interface for objects that are owned (have been added) by a specific mod.
 * Examples of this include class definitions or recipes.
 */
export type ModOwned = {
  /** The mod that owns this object */
  mod: LightweightMod;
};

/** Used to filter mods based on different criteria */
export type ModPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<ModPredicate>>;
  /** A mod will match if any of the classes it defines matches the given predicate */
  anyClass?: InputMaybe<ClassDefinitionPredicate>;
  /** A mod will match if any of the manifest attributes its jar has matches the given filter */
  anyManifestAttribute?: InputMaybe<ManifestAttributePredicate>;
  /** A mod will match if any of the artifacts it nests (this check applies to the whole tree) matches the given predicate */
  anyNestedArtifact?: InputMaybe<NestedArtifactPredicate>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<ModPredicate>>;
  /** Filter based on the mod's authors (derived from mod metadata) */
  authors?: InputMaybe<StringPredicate>;
  /** A mod will match if it is associated with the CurseForge project with the given ID */
  curseforgeProjectId?: InputMaybe<IntPredicate>;
  /** Filter based on the mod's description (derived from mod metadata) */
  description?: InputMaybe<StringPredicate>;
  /** A mod will match if it is contained in the pack with the given ID */
  inPack?: InputMaybe<PlatformProjectIdentifier>;
  /** A mod will match if the date it has last been indexed on matches the given predicate */
  indexed?: InputMaybe<DatePredicate>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the mod's license (derived from mod metadata) */
  license?: InputMaybe<StringPredicate>;
  /** Filter based on the mod's maven coordinates (only non-`null` if the mod was JiJ'd by another mod) */
  mavenCoordinates?: InputMaybe<StringPredicate>;
  /** Filter based on the mod's metadata (sourced from the platform's metadata file) */
  metadata?: InputMaybe<JsonPredicate>;
  /**
   * Filter based on the mod's mod IDs (derived from mod metadata).
   * On Neo/Forge only one of the IDs of a multi mod jar needs to match the filter.
   */
  modId?: InputMaybe<StringPredicate>;
  /** A mod will match if it is associated with the Modrinth project with the given ID */
  modrinthProjectId?: InputMaybe<StringPredicate>;
  /** Filter based on the mod's name (derived from mod metadata, jar contents or maven coordinates) */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<ModPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<ModPredicate>;
};

export type Mutation = {
  __typename?: 'Mutation';
  /**
   * Stop indexing the given game version.
   * Returns `true` if the game version was indexed before and is now no longer actively indexed.
   */
  stopIndexingGameVersion: Scalars['Boolean']['output'];
};


export type MutationStopIndexingGameVersionArgs = {
  force?: InputMaybe<Scalars['Boolean']['input']>;
  loader: Loader;
  version: Scalars['String']['input'];
};

/** Represents an artifact nested within another one, in a strictly flat structure. */
export type NestedArtifact = BaseNestedArtifact & {
  __typename?: 'NestedArtifact';
  /** The ID of this artifact */
  id: Scalars['String']['output'];
  /** The version of this artifact */
  version: Scalars['String']['output'];
};

/**
 * Represents an artifact nested within another one, as a tree.
 * This type provides access to children nested artifacts.
 */
export type NestedArtifactNode = BaseNestedArtifact & {
  __typename?: 'NestedArtifactNode';
  /** The ID of this artifact */
  id: Scalars['String']['output'];
  /** The artifacts this artifact nests further within it */
  nested: Maybe<Array<NestedArtifactNode>>;
  /** The version of this artifact */
  version: Scalars['String']['output'];
};

/** Used to filter nested artifact coordinates based on their ID and/or version */
export type NestedArtifactPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<NestedArtifactPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<NestedArtifactPredicate>>;
  /** Filter based on the nested artifact ID */
  id?: InputMaybe<StringPredicate>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<NestedArtifactPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<NestedArtifactPredicate>;
  /** Filter based on the nested artifact version */
  version?: InputMaybe<StringPredicate>;
};

export enum OrderDirection {
  Asc = 'asc',
  Desc = 'desc'
}

export type OrderMethod = {
  by?: InputMaybe<OrderRule>;
  direction?: InputMaybe<OrderDirection>;
};

export enum OrderRule {
  Length = 'length',
  Value = 'value'
}

/** Information about pagination in a connection. */
export type PageInfo = {
  __typename?: 'PageInfo';
  /** The cursor ID that points to the last element returned, if there is one */
  endCursor: Maybe<Scalars['ID']['output']>;
  /** Whether there are more elements past the last element of the response */
  hasNextPage: Scalars['Boolean']['output'];
  /**
   * Whether there are more elements before the last element of the response.
   * Note that this field may be false even if earlier pages exist when it is too expensive to check if earlier pages exist.
   */
  hasPreviousPage: Scalars['Boolean']['output'];
  /** The cursor ID that points to the first element returned, if there is one */
  startCursor: Maybe<Scalars['ID']['output']>;
};

/** Permissions that a user can have */
export enum Permission {
  /** The user has the permission to manage the indexed versions (stop indexing, request that a new version is indexed, etc.) */
  ManageVersions = 'MANAGE_VERSIONS'
}

/** Represents the ID/slug of a project on CurseForge or Modrinth */
export type PlatformProjectIdentifier = {
  /** The ID of the project on CurseForge */
  curseforge?: InputMaybe<Scalars['Int']['input']>;
  /** The slug of the project on CurseForge */
  curseforgeSlug?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the project on Modrinth */
  modrinth?: InputMaybe<Scalars['String']['input']>;
  /** The slug of the project on Modrinth */
  modrinthSlug?: InputMaybe<Scalars['String']['input']>;
};

export type Query = {
  __typename?: 'Query';
  /** Query a game version based on the Minecraft version and the loader */
  gameVersion: Maybe<GameVersion>;
  /** Get all indexed game versions */
  gameVersions: Array<GameVersion>;
};


export type QueryGameVersionArgs = {
  loader: Loader;
  version: Scalars['String']['input'];
};


export type QueryGameVersionsArgs = {
  loader?: InputMaybe<Loader>;
};

/** A Minecraft recipe file */
export type RecipeFile = ModOwned & {
  __typename?: 'RecipeFile';
  /** The mod that owns this object */
  mod: LightweightMod;
  /** The name of this recipe (e.g. `minecraft:composter`) */
  name: Scalars['String']['output'];
  /** The contents of the recipe file, in JSON format. This object does not include the `type`. */
  recipe: Scalars['JSON']['output'];
  /** The type of this recipe (e.g. `minecraft:crafting_shaped`) */
  type: Scalars['String']['output'];
};

/** A connection (list) composed of `RecipeFile`. */
export type RecipeFileConnection = {
  __typename?: 'RecipeFileConnection';
  /** Identifies the amount of items in the returned edges. */
  count: Scalars['Int']['output'];
  /** A list of edges. */
  edges: Array<RecipeFileEdge>;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
};

/** An edge of a `RecipeFile`. */
export type RecipeFileEdge = {
  __typename?: 'RecipeFileEdge';
  /** The cursor ID of the element. */
  cursor: Scalars['ID']['output'];
  /** The item at the end of the edge. */
  node: RecipeFile;
};

/** Used to filter recipes based on different criteria */
export type RecipeFilePredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<RecipeFilePredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<RecipeFilePredicate>>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the mod that defines the recipe */
  mod?: InputMaybe<ModPredicate>;
  /** Filter based on the recipe name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<RecipeFilePredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<RecipeFilePredicate>;
  /** Filter based on the recipe JSON (the object given to this predicate does not include the type) */
  recipe?: InputMaybe<JsonPredicate>;
  /** Filter based on the recipe type */
  type?: InputMaybe<StringPredicate>;
};

/** Represents a reference in the form of the class referencing the method/field and how many times it is referenced in that class */
export type Reference = {
  __typename?: 'Reference';
  /** The class in which this reference is contained */
  owner: ClassDefinition;
  /** How many times the referenced method/field was referenced in the class */
  referenceCount: Scalars['Int']['output'];
};

/** Type used to order `Reference`s */
export type ReferenceOrdering = {
  /** Order by referenceCount */
  referenceCount?: InputMaybe<OrderMethod>;
};

/** Used to filter references based on different criteria */
export type ReferencePredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<ReferencePredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<ReferencePredicate>>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the mod that the reference is part of */
  mod?: InputMaybe<ModPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<ReferencePredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<ReferencePredicate>;
  /** Filter based on the amount of references */
  referenceCount?: InputMaybe<IntPredicate>;
};

/**
 * Represents a type that can be referenced by other classes.
 * Can be method or a field.
 */
export type Referenceable = {
  /** Returns the references to this element */
  references: Array<Reference>;
};


/**
 * Represents a type that can be referenced by other classes.
 * Can be method or a field.
 */
export type ReferenceableReferencesArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  order?: InputMaybe<ReferenceOrdering>;
  where?: InputMaybe<ReferencePredicate>;
};

/** Represents a reference to a field within a concrete class definition */
export type ReferencedField = {
  __typename?: 'ReferencedField';
  /** The name of the class of the referenced field */
  class: Scalars['String']['output'];
  /** The name of the referenced field */
  name: Scalars['String']['output'];
  /** How many times this field was referenced in the class definition */
  referenceCount: Scalars['Int']['output'];
  /** The type of the referenced field */
  type: Scalars['String']['output'];
};

/** Represents a reference to a method within a concrete class definition */
export type ReferencedMethod = {
  __typename?: 'ReferencedMethod';
  /** The name of the class of the referenced method */
  class: Scalars['String']['output'];
  /** The descriptor of the referenced method */
  descriptor: Scalars['String']['output'];
  /** The name of the referenced method */
  name: Scalars['String']['output'];
  /** How many times this method was referenced in the class definition */
  referenceCount: Scalars['Int']['output'];
};

/**
 * Represents a way of filtering string inputs based on different rules that are either logical operators
 * on other filters (NOT, AND, OR) or exact / regex matches.
 */
export type StringPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<StringPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<StringPredicate>>;
  /** The string being filtered must be equal to the given string. */
  equals?: InputMaybe<Scalars['String']['input']>;
  /** The string being filtered must be equal to at least one of the given strings. */
  isIn?: InputMaybe<Array<Scalars['String']['input']>>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** The string being filtered will pass this predicate if its length matches the given predicate. */
  length?: InputMaybe<IntPredicate>;
  /** The string being filtered must match the given regex pattern. */
  matches?: InputMaybe<Scalars['String']['input']>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<StringPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<StringPredicate>;
  /** The string being filtered must start with the given string. */
  startsWith?: InputMaybe<Scalars['String']['input']>;
};

/** A tag entry */
export type TagEntry = ModOwned & {
  __typename?: 'TagEntry';
  /**
   * The entry that is added to the tag.
   * This might be a fully qualified registry object (e.g. `minecraft:spruce_door`) or another tag (e.g. `#minecraft:planks`).
   */
  entry: Scalars['String']['output'];
  /** The mod that owns this object */
  mod: LightweightMod;
  /** The name of this tag */
  tag: Scalars['String']['output'];
};

/** A connection (list) composed of `TagEntry`. */
export type TagEntryConnection = {
  __typename?: 'TagEntryConnection';
  /** Identifies the amount of items in the returned edges. */
  count: Scalars['Int']['output'];
  /** A list of edges. */
  edges: Array<TagEntryEdge>;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
};

/** An edge of a `TagEntry`. */
export type TagEntryEdge = {
  __typename?: 'TagEntryEdge';
  /** The cursor ID of the element. */
  cursor: Scalars['ID']['output'];
  /** The item at the end of the edge. */
  node: TagEntry;
};

/** Used to filter tag entries based on different criteria */
export type TagEntryPredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<TagEntryPredicate>>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<TagEntryPredicate>>;
  /** Filter based on the entry of the tag */
  entry?: InputMaybe<StringPredicate>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the mod that defines this tag entry */
  mod?: InputMaybe<ModPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<TagEntryPredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<TagEntryPredicate>;
  /** Filter based on the tag name */
  tag?: InputMaybe<StringPredicate>;
};

/** A Minecraft tag file */
export type TagFile = {
  __typename?: 'TagFile';
  /** The entries added to this tag. These can be either normal objects (e.g. minecraft:stone) or other tags, denoted by a # at the start (e.g. #minecraft:head_armor) */
  entries: Array<Scalars['String']['output']>;
  /** The name of this tag (e.g. minecraft:mineable/pickaxe) */
  name: Scalars['String']['output'];
  /**
   * Whether this tag overrides all previously added entries.
   * Corresponds to the "replace" field in the tag JSON.
   */
  replace: Scalars['Boolean']['output'];
};


/** A Minecraft tag file */
export type TagFileEntriesArgs = {
  where?: InputMaybe<StringPredicate>;
};

/** Used to filter tags based on different criteria */
export type TagFilePredicate = {
  /** Match if all of the given filters match */
  allOf?: InputMaybe<Array<TagFilePredicate>>;
  /** A tag file will match this predicate if any of its direct entries matches the given predicate */
  anyEntry?: InputMaybe<StringPredicate>;
  /** Match if any of the given filters matches */
  anyOf?: InputMaybe<Array<TagFilePredicate>>;
  /** If `true`, match if the value tested is null. Otherwise, match if non-null. */
  isNull?: InputMaybe<Scalars['Boolean']['input']>;
  /** Filter based on the tag name */
  name?: InputMaybe<StringPredicate>;
  /**
   * Match if none of the given filters match.
   * Prefer using this instead of `not(anyOf(...))` as it is generally faster.
   */
  noneOf?: InputMaybe<Array<TagFilePredicate>>;
  /** Invert the given filter */
  not?: InputMaybe<TagFilePredicate>;
  /**
   * If set to true, only tags that override existing values will be returned.
   * If set to false, only tags that do not override existing values will be returned.
   */
  replace?: InputMaybe<Scalars['Boolean']['input']>;
};

export type GetImplementationsQueryVariables = Exact<{
  version: Scalars['String']['input'];
  loader: Loader;
  class: Scalars['String']['input'];
}>;


export type GetImplementationsQuery = { gameVersion: { __typename?: 'GameVersion', class: { __typename?: 'Class', inheritors: Array<{ __typename?: 'InheritanceTreeClass', name: string, definitions: Array<{ __typename?: 'ClassDefinition', mod: { __typename?: 'LightweightMod', id: number, name: string } }> }> } | null } | null };

export type GetClassesAnnotatedQueryVariables = Exact<{
  version: Scalars['String']['input'];
  loader: Loader;
  predicate: ClassDefinitionPredicate;
  annotationPredicate: AnnotationPredicate;
  cursor?: InputMaybe<Scalars['ID']['input']>;
}>;


export type GetClassesAnnotatedQuery = { gameVersion: { __typename?: 'GameVersion', classDefinitions: { __typename?: 'ClassDefinitionConnection', pageInfo: { __typename?: 'PageInfo', hasNextPage: boolean, endCursor: string | null }, edges: Array<{ __typename?: 'ClassDefinitionEdge', node: { __typename?: 'ClassDefinition', name: string, mod: { __typename?: 'LightweightMod', id: number, name: string }, annotations: Array<{ __typename?: 'Annotation', value: any | null }> } }> } } | null };

export type GetRecipesQueryVariables = Exact<{
  version: Scalars['String']['input'];
  loader: Loader;
  predicate: RecipeFilePredicate;
  cursor?: InputMaybe<Scalars['ID']['input']>;
}>;


export type GetRecipesQuery = { gameVersion: { __typename?: 'GameVersion', recipes: { __typename?: 'RecipeFileConnection', pageInfo: { __typename?: 'PageInfo', hasNextPage: boolean, endCursor: string | null }, edges: Array<{ __typename?: 'RecipeFileEdge', node: { __typename?: 'RecipeFile', name: string, recipe: any, type: string, mod: { __typename?: 'LightweightMod', id: number, name: string } } }> } } | null };

export type GetTagEntriesQueryVariables = Exact<{
  version: Scalars['String']['input'];
  loader: Loader;
  registry: Scalars['String']['input'];
  predicate: TagEntryPredicate;
  cursor?: InputMaybe<Scalars['ID']['input']>;
}>;


export type GetTagEntriesQuery = { gameVersion: { __typename?: 'GameVersion', tagEntries: { __typename?: 'TagEntryConnection', pageInfo: { __typename?: 'PageInfo', hasNextPage: boolean, endCursor: string | null }, edges: Array<{ __typename?: 'TagEntryEdge', node: { __typename?: 'TagEntry', tag: string, entry: string, mod: { __typename?: 'LightweightMod', id: number, name: string } } }> } } | null };

export type GetEnumExtensionsQueryVariables = Exact<{
  version: Scalars['String']['input'];
  loader: Loader;
  enum: Scalars['String']['input'];
  cursor?: InputMaybe<Scalars['ID']['input']>;
}>;


export type GetEnumExtensionsQuery = { gameVersion: { __typename?: 'GameVersion', enumExtensions: { __typename?: 'EnumExtensionConnection', pageInfo: { __typename?: 'PageInfo', hasNextPage: boolean, endCursor: string | null }, edges: Array<{ __typename?: 'EnumExtensionEdge', node: { __typename?: 'EnumExtension', name: string, constructor: string, parameters: any, mod: { __typename?: 'LightweightMod', id: number, name: string, enumExtensionsFile: any | null } } }> } } | null };

export type GetFieldReferencesQueryVariables = Exact<{
  version: Scalars['String']['input'];
  loader: Loader;
  class: Scalars['String']['input'];
  fieldFilter: FieldPredicate;
  filter?: InputMaybe<ReferencePredicate>;
}>;


export type GetFieldReferencesQuery = { gameVersion: { __typename?: 'GameVersion', class: { __typename?: 'Class', fields: Array<{ __typename?: 'Field', name: string, type: string, references: Array<{ __typename?: 'Reference', owner: { __typename?: 'ClassDefinition', name: string, mod: { __typename?: 'LightweightMod', id: number, name: string } } }> }> } | null } | null };

export type GetMethodReferencesQueryVariables = Exact<{
  version: Scalars['String']['input'];
  loader: Loader;
  class: Scalars['String']['input'];
  methodFilter: MethodPredicate;
  filter?: InputMaybe<ReferencePredicate>;
}>;


export type GetMethodReferencesQuery = { gameVersion: { __typename?: 'GameVersion', class: { __typename?: 'Class', methods: Array<{ __typename?: 'Method', name: string, descriptor: string, references: Array<{ __typename?: 'Reference', owner: { __typename?: 'ClassDefinition', name: string, mod: { __typename?: 'LightweightMod', id: number, name: string } } }> }> } | null } | null };

export type PlatformInformationFragment = { __typename?: 'IntPlatformInformation', title: string, downloads: number, description: string, projectUrl: string, issuesUrl: string | null, iconUrl: string, sourceUrl: string | null };

export type GetModInformationQueryVariables = Exact<{
  version: Scalars['String']['input'];
  loader: Loader;
  id: Scalars['Int']['input'];
}>;


export type GetModInformationQuery = { gameVersion: { __typename?: 'GameVersion', _modInformation: { __typename?: 'IntModInformation', name: string, authors: string | null, modIds: Array<string> | null, license: string | null, version: string, metadata: any | null, mavenCoordinates: string | null, curseforge: { __typename?: 'IntPlatformInformation', fileDownloadUrl: string | null, title: string, downloads: number, description: string, projectUrl: string, issuesUrl: string | null, iconUrl: string, sourceUrl: string | null } | null, modrinth: { __typename?: 'IntPlatformInformation', fileDownloadUrl: string | null, title: string, downloads: number, description: string, projectUrl: string, issuesUrl: string | null, iconUrl: string, sourceUrl: string | null } | null } } | null };
