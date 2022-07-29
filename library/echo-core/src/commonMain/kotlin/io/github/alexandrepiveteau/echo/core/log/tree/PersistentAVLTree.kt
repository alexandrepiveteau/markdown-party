package io.github.alexandrepiveteau.echo.core.log.tree

import io.github.alexandrepiveteau.echo.core.log.tree.PersistentAVLTree.AVLNode
import io.github.alexandrepiveteau.echo.core.log.tree.PersistentAVLTree.AVLNode.Diagram.Companion.EmptyNode
import kotlin.math.max

/**
 * A persistent AVL Tree data structure.
 *
 * @param T the type of the elements in the [PersistentAVLTree].
 * @property root the root [AVLNode]. Might be null if the tree is empty.
 */
internal class PersistentAVLTree<T : Comparable<T>>
private constructor(
    private val root: AVLNode<T>?,
) {

  /**
   * A node within an [PersistentAVLTree], which may contain some values of type [T].
   *
   * @param T the type of the value within the [AVLNode].
   *
   * @param value the value of the node.
   * @param left the left child of this node, if it exists.
   * @param right the right child of this node, if it exists.
   */
  private data class AVLNode<out T>(
      val value: T,
      val left: AVLNode<T>?,
      val right: AVLNode<T>?,
  ) {

    /** The height of the tree. At least 1. */
    val height: Int = kotlin.math.max(left?.height ?: 0, right?.height ?: 0) + 1

    /** The balance factor of an [AVLNode]. Must be in the range [-2, 2]. */
    private val balance: Int
      get() = (right?.height ?: 0) - (left?.height ?: 0)

    /**
     * Balances the [AVLNode] with a balance factor in the range [-2, 2], by applying some
     * rotations, and returns a new balanced node with a [balance] in the range [-1, 1].
     */
    fun balance(): AVLNode<T> =
        when (this.balance) {
          -2 ->
              when (checkNotNull(left).balance) {
                1 -> rotateLeftRight()
                else -> rotateRight()
              }
          2 ->
              when (checkNotNull(right).balance) {
                -1 -> rotateRightLeft()
                else -> rotateLeft()
              }
          else -> this // The node is already balanced.
        }

    /**
     * Rotates the [AVLNode] to the left. This corresponds to the following transformation if
     * [rotateLeft] is applied to `A` :
     *
     * ```
     *     A              B
     *    / \            / \
     *   a   B    =>    A   c
     *      / \        / \
     *     b   c      a   b
     * ```
     *
     * @return the new root of the subtree.
     */
    private fun rotateLeft(): AVLNode<T> {
      val pivot = checkNotNull(right) { "Can't rotate left when no right child." }
      val newLeft = this.copy(right = pivot.left)
      return pivot.copy(left = newLeft)
    }

    /**
     * Rotates the [AVLNode] to the right. This corresponds to the following transformation if
     * [rotateRight] is applied to `B` :
     *
     * ```
     *       B          A
     *      / \        / \
     *     A   c  =>  a   B
     *    / \            / \
     *   a   b          b   c
     * ```
     *
     * @return the new root of the subtree.
     */
    private fun rotateRight(): AVLNode<T> {
      val pivot = checkNotNull(left) { "Can't rotate right when no left child." }
      val newRight = this.copy(left = pivot.right)
      return pivot.copy(right = newRight)
    }

    /**
     * Rotates the [AVLNode] to the left, and then to the right. This corresponds to the following
     * transformations if [rotateLeftRight] is applied to `A` :
     *
     * ```
     *       A              A            C
     *      / \            / \          / \
     *     B   d          C   d        B   A
     *    / \     =>     / \     =>   /|   |\
     *   a  C           B   c        a b   c d
     *     / \         / \
     *    b   c       a   b
     * ```
     */
    private fun rotateLeftRight(): AVLNode<T> {
      val left = checkNotNull(left) { "Can't rotate left-right when no left child." }
      val rotated = left.rotateLeft()
      return copy(left = rotated).rotateRight()
    }

    /**
     * Rotates the [AVLNode] to the right, and then to the left. This corresponds to the following
     * transformations if [rotateRightLeft] is applied to `A` :
     *
     * ```
     *     A            A                C
     *    / \          / \              / \
     *   a   B        a   C            A   B
     *      / \   =>     / \     =>   /|   |\
     *     C   d        b   B        a b   c d
     *    / \              / \
     *   b   c            c   d
     * ```
     */
    private fun rotateRightLeft(): AVLNode<T> {
      val right = checkNotNull(right) { "Can't rotate right-left when no right child." }
      val rotated = right.rotateRight()
      return copy(right = rotated).rotateLeft()
    }

    /** Returns the maximum node of this [AVLNode], whose [right] will be `null`. */
    fun max(): AVLNode<T> {
      var current = this
      while (true) current = current.right ?: return current
    }

    /** Returns the minimum value of this [AVLNode], whose [left] will be `null`. */
    fun min(): AVLNode<T> {
      var current = this
      while (true) current = current.left ?: return current
    }

    // String representation of the nodes / trees.

    /** A [Diagram] represents a block which can render a subtree. */
    private interface Diagram {

      /** The index of the anchor line. */
      val anchor: Int

      /** The width of the rendered tree. */
      val width: Int

      /** The height of the rendered tree. */
      val height: Int

      /** Renders the [Diagram] to a [String]. */
      fun render(): String

      companion object {

        /** The text which should be displayed for empty nodes. */
        const val EmptyNode = "[EMPTY]"
      }
    }

    private data class TextDiagram(val text: String) : Diagram {
      private val lines = text.lines()
      override val width = lines.maxOfOrNull { it.length } ?: 0
      override val height = lines.size
      override val anchor = height / 2
      override fun render() = lines.joinToString(separator = "\n", transform = { it.padEnd(width) })
    }

    private data class CombinedDiagram(
        val value: Diagram,
        val left: Diagram,
        val right: Diagram,
    ) : Diagram {
      override val width: Int = value.width + 3 + max(left.width, right.width)
      override val height: Int = max(value.height, left.height + 1 + right.height)
      override val anchor: Int = height / 2 - value.height / 2 + value.anchor
      override fun render(): String {
        val result = Array(height) { CharArray(width) { ' ' } }

        /** Renders the given [text] at the [x] and [y] coordinates. */
        fun renderAt(x: Int, y: Int, text: String) {
          val lines = text.lines()
          for (j in lines.indices) {
            val line = lines[j]
            for (i in line.indices) {
              result[y + j][x + i] = line[i]
            }
          }
        }

        renderAt(0, height / 2 - value.height / 2, value.render())
        renderAt(value.width + 3, 0, right.render())
        renderAt(value.width + 3, height - left.height, left.render())

        // Draw the lines.
        for (j in right.anchor..height - left.height + left.anchor) result[j][value.width + 1] = '|'
        result[anchor][value.width + 1] = '+'
        result[right.anchor][value.width + 1] = '+'
        result[height - left.height + left.anchor][value.width + 1] = '+'
        result[anchor][value.width] = '-'
        result[right.anchor][value.width + 2] = '-'
        result[height - left.height + left.anchor][value.width + 2] = '-'

        return result.joinToString("\n") { it.joinToString("") }
      }
    }

    /** Transforms this [AVLNode] to a [Diagram]. */
    private fun toDiagram(): Diagram =
        CombinedDiagram(
            value = TextDiagram(value.toString()),
            left = left?.toDiagram() ?: TextDiagram(EmptyNode),
            right = right?.toDiagram() ?: TextDiagram(EmptyNode),
        )

    override fun toString(): String = toDiagram().render()
  }

  /** Creates an empty [PersistentAVLTree]. */
  constructor() : this(null)

  /**
   * Returns true iff the given [value] is contained within the [PersistentAVLTree], in O(log(n)).
   *
   * @param value the value whose presence is checked.
   * @return true iff the value is present in the tree.
   */
  operator fun contains(value: T): Boolean {
    var current = root
    while (current != null) {
      current =
          when {
            value > current.value -> current.right
            value < current.value -> current.left
            else -> return true
          }
    }
    return false
  }

  /**
   * Inserts the given [value] in the [PersistentAVLTree], in O(log(n)).
   *
   * @param value the item which is inserted.
   */
  operator fun plus(value: T): PersistentAVLTree<T> = PersistentAVLTree(add(root, value))

  /**
   * Inserts the given [value] in the provided [root], and returns the updated [AVLNode].
   *
   * @param root the [AVLNode] in which the value is inserted.
   * @param value the inserted value.
   * @return the updated root [AVLNode].
   */
  private fun add(root: AVLNode<T>?, value: T): AVLNode<T> =
      when {
        root == null -> AVLNode(value = value, left = null, right = null)
        value < root.value -> root.copy(left = add(root.left, value)).balance()
        value > root.value -> root.copy(right = add(root.right, value)).balance()
        else -> root // Skip the insertion on duplicate entries.
      }

  /**
   * Removes the given [value] in the [PersistentAVLTree], in O(log(n)).
   *
   * @param value the item which is removed.
   */
  operator fun minus(value: T): PersistentAVLTree<T> = PersistentAVLTree(remove(root, value))

  /**
   * Inserts the given [value] in the provided [root], and returns the updated [AVLNode].
   *
   * @param root the [AVLNode] in which the value is removed.
   * @param value the removed value.
   * @return the updated root [AVLNode], or null if empty.
   */
  private fun remove(root: AVLNode<T>?, value: T): AVLNode<T>? =
      when {
        root == null -> null
        value < root.value -> root.copy(left = remove(root.left, value)).balance()
        value > root.value -> root.copy(right = remove(root.right, value)).balance()
        else ->
            when {
              root.left == null && root.right == null -> null // no children
              root.left == null -> root.right // right is not null and balanced
              root.right == null -> root.left // left is not null and balanced
              else -> {
                val successor = root.right.min()
                val newRight = remove(root.right, successor.value)
                root.copy(value = successor.value, right = newRight).balance()
              }
            }
      }

  override fun toString(): String = root.toString()
}