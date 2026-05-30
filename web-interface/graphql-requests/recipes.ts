import gql from "graphql-tag";
import type {TypedDocumentNode} from "@apollo/client";
import type {
  GetRecipesQuery,
  GetRecipesQueryVariables,
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
