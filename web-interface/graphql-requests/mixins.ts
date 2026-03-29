import gql from "graphql-tag";
import type {TypedDocumentNode} from "@apollo/client";
import type {GetMixinsQuery, GetMixinsQueryVariables} from "./types/__generated__/graphql";

export const MIXINS_ANNOTATION_PREDICATE: TypedDocumentNode<
    GetMixinsQuery,
    GetMixinsQueryVariables
> = gql`
  query GetMixins($version: String!, $loader: Loader!, $predicate: AnnotationPredicate!, $cursor: ID) {
    gameVersion(loader: $loader, version: $version) {
      classDefinitions(
        where: {
          anyAnnotation: {
            allOf: [
              {type: {equals: "org/spongepowered/asm/mixin/Mixin"}}
              $predicate
            ]
          }
        },
        after: $cursor
      ) {
        pageInfo {
          hasNextPage
          endCursor
        }
        edges {
          node {
            name
            mod {
              name
              curseforgeProjectId
              modrinthProjectId
            }
            annotations(where: {type: {equals: "org/spongepowered/asm/mixin/Mixin"}}) {
              type
              value
            }
          }
        }
      }
    }
  }
`;
