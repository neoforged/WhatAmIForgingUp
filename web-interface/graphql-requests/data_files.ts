import gql from "graphql-tag";
import type {TypedDocumentNode} from "@apollo/client";
import type {
  GetRecipesQuery,
  GetRecipesQueryVariables, GetTagEntriesQuery, GetTagEntriesQueryVariables,
} from "./types/__generated__/graphql";

export const RECIPES: TypedDocumentNode<
    GetRecipesQuery,
    GetRecipesQueryVariables
> = gql`
    query GetRecipes($version: String!, $loader: Loader!, $predicate: RecipeFilePredicate!, $cursor: ID) {
        gameVersion(loader: $loader, version: $version) {
            recipes(where: $predicate, after: $cursor) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                edges {
                    node {
                        name
                        recipe
                        type
                        mod {
                            id
                            name
                        }
                    }
                }
            }
        }
    }
`;

export const TAG_ENTRIES: TypedDocumentNode<
    GetTagEntriesQuery,
    GetTagEntriesQueryVariables
> = gql`
    query GetTagEntries($version: String!, $loader: Loader!, $registry: String!, $predicate: TagEntryPredicate!, $cursor: ID) {
        gameVersion(loader: $loader, version: $version) {
            tagEntries(registry: $registry, where: $predicate, after: $cursor) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                edges {
                    node {
                        tag
                        entry
                        mod {
                            id
                            name
                        }
                    }
                }
            }
        }
    }
`;
