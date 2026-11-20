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
    
    query GetModInformation($version: String!, $loader: Loader!, $id: Int!) {
        gameVersion(loader: $loader, version: $version) {
            _modInformation(id: $id) {
                name
                authors
                modIds
                license
                version
                
                metadata
                
                mavenCoordinates
                curseforge {
                    ...PlatformInformation
                    fileDownloadUrl(version: $version, loader: $loader)
                }
                modrinth {
                    ...PlatformInformation
                    fileDownloadUrl(version: $version, loader: $loader)
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
