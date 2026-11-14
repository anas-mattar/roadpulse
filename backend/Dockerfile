# RoadPulse API container (ASP.NET Core, .NET 10). Build from the backend/ folder:
#   docker build -t roadpulse-api .
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY RoadPulse.sln ./
COPY src/RoadPulse.Api/RoadPulse.Api.csproj src/RoadPulse.Api/
COPY tests/RoadPulse.Api.Tests/RoadPulse.Api.Tests.csproj tests/RoadPulse.Api.Tests/
RUN dotnet restore src/RoadPulse.Api/RoadPulse.Api.csproj
COPY src/ src/
RUN dotnet publish src/RoadPulse.Api/RoadPulse.Api.csproj -c Release -o /app --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app ./
ENV ASPNETCORE_URLS=http://+:8080
EXPOSE 8080
USER app
ENTRYPOINT ["dotnet", "RoadPulse.Api.dll"]
