import gql from "graphql-tag";
import type {TypedDocumentNode} from "@apollo/client";
import type {
  GetModDownloadUrlsQuery,
  GetModDownloadUrlsQueryVariables,
  GetModInformationQuery,
  GetModInformationQueryVariables
} from "./types/__generated__/graphql";

export const MOD_INFORMATION: TypedDocumentNode<
  GetModInformationQuery,
  GetModInformationQueryVariables
> = gql`
    fragment PlatformInformation on IntPlatformInformation {
        title
        downloads
        description
        
        projectUrl
        issuesUrl
        iconUrl
        sourceUrl
    }
    
    query GetModInformation($version: String!, $loader: Loader!, $ids: [Int!]!, $includeDownloadUrl: Boolean!) {
        gameVersion(loader: $loader, version: $version) {
            _modInformation(ids: $ids) {
                name
                authors
                modIds
                license
                version
                
                metadata
                
                mavenCoordinates
                curseforge {
                    ...PlatformInformation
                    fileDownloadUrl(version: $version, loader: $loader) @include(if: $includeDownloadUrl)
                }
                modrinth {
                    ...PlatformInformation
                    fileDownloadUrl(version: $version, loader: $loader) @include(if: $includeDownloadUrl)
                }
            }
        }
    }
`;

export const MOD_DOWNLOAD_URLS: TypedDocumentNode<
  GetModDownloadUrlsQuery,
  GetModDownloadUrlsQueryVariables
> = gql`
    query GetModDownloadUrls($version: String!, $loader: Loader!, $ids: [Int!]!) {
        gameVersion(loader: $loader, version: $version) {
            _modDownloadUrls(ids: $ids) {
                modId
                downloadUrl
            }
        }
    }
`;
