# Set Game
BGU System Programming (SPL) course 2nd assignment. Written in Java and build with Maven. 

This is an implementation of the [card game Set](https://en.wikipedia.org/wiki/Set_(card_game)) with GUI, including PvP mode and optional bot players.

<img src="Images/Set%20logo.png" width="400" height="400">

# How To Build
1. Clone the repository to your local machine.
2. Navigate to the project directory in your terminal.
3. Compile the source files using the following Maven command:

> mvn clean compile exec:java

# Project Overview
## Project Objectives
The goal of the assignment is to practice concurrent multi-threaded programming on Java environment.

## Features
- The game support up to 2 human players. Key layout:

![Key layout](/Images/Key%20layout.png)

- Up to 4 bot players (4 players overall)
- Configurable game settings like time for each round, number of players, key layout, font size and more. Visit the Set Game [Config class](Skeleton/src/main/java/bguspl/set/Config.java) for more info.
- Optional hints for available sets on the board.




## This version of the game Set
The game contains a deck of 81 cards. Each card contains a drawing with four features (color, number, shape, shading).

The game starts with 12 drawn cards from the deck that are placed on a 3x4 grid on the table.
The goal of each player is to find a combination of three cards from the cards on the table that are said to make up a “legal set”.

A “legal set” is defined as a set of 3 cards, that for each one of the four features — color, number, shape, and shading — the three cards must display that feature as either: (a) all the same, or: (b) all different (in other words, for each feature the three cards must avoid having two cards showing one version of the feature and the remaining card showing a different version).

The possible values of the features are:
- **The color**: red, green or purple.
- **The number of shapes**: 1, 2 or 3.
- **The geometry of the shapes**: squiggle, diamond or oval.
- **The shading of the shapes**: solid, partial or empty.

For example:

![example 1](/Images/example%201.png)
![example 2](/Images/example%202.png)

The players play together simultaneously on the table, trying to find a legal set of 3 cards. They do so by placing tokens on the cards, and once they place the third token, they should ask the dealer to check if the set is legal.

![board](/Images/board.png)

If the set is not legal, the player gets a penalty, freezing his ability of removing or placing his tokens for a specified time period.

If the set is a legal set, the dealer will discard the cards that form the set from the table, replace them with 3 new cards from the deck and give the successful player one point. In this case the player also gets frozen although for a shorter time period.

To keep the game more interesting and dynamic, and in case no legal sets are currently available on the table, once every minute the dealer collects all the cards from the table, reshuffles the deck and draws them anew.

The game will continue as long as there is a legal set to be found in the remaining cards (that are either on table or in the deck). When there is no legal set left, the game will end and the player with the most points will be declared as the winner!

## System Requirements
- Java
- Maven version 3.6.3 or later
