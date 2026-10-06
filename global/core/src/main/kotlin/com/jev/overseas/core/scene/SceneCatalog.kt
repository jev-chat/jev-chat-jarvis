package com.jev.overseas.core.scene

/** Lookup of scenes, relationship types and the sentence that describes a selection to the models. */
object SceneCatalog {

    val specs: Map<Scene, SceneSpec> = mapOf(
        Scene.WORK to WorkScene,
        Scene.ROMANCE to RomanceScene,
        Scene.FRIENDS to FriendsScene,
        Scene.FAMILY to FamilyScene,
        Scene.GENERAL to GeneralScene,
    )

    fun spec(scene: Scene): SceneSpec = specs.getValue(scene)

    fun scene(id: String): Scene? = Scene.values().firstOrNull { it.id == id }

    fun relationship(scene: Scene, id: String?): RelationshipType? =
        spec(scene).relationships.firstOrNull { it.id == id }

    /** The plain-English sentence given to both models: who the two people are and what is ordinary between them. */
    fun relationshipNote(selection: Selection, direction: Direction? = null): String {
        val base = selection.relationship?.note ?: when (selection.scene) {
            Scene.WORK -> WorkScene.UNSPECIFIED_NOTE
            Scene.ROMANCE -> RomanceScene.UNSPECIFIED_NOTE
            Scene.FRIENDS -> FriendsScene.UNSPECIFIED_NOTE
            Scene.FAMILY -> FamilyScene.UNSPECIFIED_NOTE
            Scene.GENERAL -> GeneralScene.UNSPECIFIED.note
        }
        val conflict = if (!selection.conflict) "" else when (selection.scene) {
            Scene.WORK -> WorkScene.CONFLICT_NOTE
            Scene.ROMANCE -> RomanceScene.CONFLICT_NOTE
            Scene.FRIENDS -> FriendsScene.CONFLICT_NOTE
            Scene.FAMILY -> FamilyScene.CONFLICT_NOTE
            Scene.GENERAL -> ""
        }
        return base + conflict + (direction?.let { " " + it.note } ?: "")
    }
}
